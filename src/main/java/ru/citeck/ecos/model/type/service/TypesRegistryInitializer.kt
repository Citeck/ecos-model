package ru.citeck.ecos.model.type.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.service.LocalAppService
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.data.entity.EntityWithMeta
import ru.citeck.ecos.model.lib.aspect.dto.AspectInfo
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef
import ru.citeck.ecos.model.type.eapps.handler.TypeArtifactHandler
import ru.citeck.ecos.model.type.service.resolver.AspectsProvider
import ru.citeck.ecos.model.type.service.resolver.TypeDefResolver
import ru.citeck.ecos.model.type.service.resolver.TypesProvider
import ru.citeck.ecos.txn.lib.TxnContext
import ru.citeck.ecos.webapp.api.EcosWebAppApi
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.promise.Promise
import ru.citeck.ecos.webapp.api.promise.Promises
import ru.citeck.ecos.webapp.lib.model.aspect.dto.AspectDef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import ru.citeck.ecos.webapp.lib.registry.EcosRegistry
import ru.citeck.ecos.webapp.lib.registry.EcosRegistryProps
import ru.citeck.ecos.webapp.lib.registry.MutableEcosRegistry
import ru.citeck.ecos.webapp.lib.registry.init.EcosRegistryInitializer
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

@Component
class TypesRegistryInitializer(
    private val typesService: TypesService,
    private val localAppService: LocalAppService
) : EcosRegistryInitializer<TypeDef>, DisposableBean {

    companion object {
        const val ORDER = -10f

        private val log = KotlinLogging.logger {}
    }

    private val resolver = TypeDefResolver()

    private lateinit var aspectsRegistry: EcosRegistry<AspectDef>
    private lateinit var typesHierarchyUpdater: TypesHierarchyUpdater
    private lateinit var webAppApi: EcosWebAppApi
    private val initialized = AtomicBoolean()
    private val registeredTypes = HashSet<String>()

    override fun init(
        registry: MutableEcosRegistry<TypeDef>,
        values: Map<String, EntityWithMeta<TypeDef>>,
        props: EcosRegistryProps.Initializer
    ): Promise<*> {

        val rawProv = TypesServiceBasedProv(typesService, loadClasspathTypes())
        val resProv = RegistryBasedTypesProv(registry)
        val aspectsProv = AspectsProv()

        typesHierarchyUpdater = TypesHierarchyUpdater(
            typesService,
            resolver,
            rawProv,
            resProv,
            aspectsProv,
            registry,
            webAppApi
        ) {
            syncAllTypes(registry, rawProv, aspectsProv)
        }

        syncAllTypes(registry, rawProv, aspectsProv)
        typesHierarchyUpdater.start()

        typesService.addOnDeletedListener {
            TxnContext.doAfterCommit(0f, false) {
                registry.setValue(it, null)
            }
        }

        fun updateTypes(typeIds: Collection<String>) {
            val typesSet = if (typeIds !is Set<String>) typeIds.toSet() else typeIds
            typesHierarchyUpdater.updateTypes(typesSet)
        }

        typesService.addListenerTypeHierarchyChangedListener { changedTypes ->
            updateTypes(changedTypes)
        }

        aspectsRegistry.listenEvents { id, _, _ ->
            val key = this::class.java.simpleName + ".types-with-aspects-to-update"
            TxnContext.processSetAfterCommit(key, id) { changedAspects ->
                val changedTypes = typesService.getAll().filter { rec ->
                    rec.aspects.any { changedAspects.contains(it.ref.getLocalId()) }
                }.map { it.id }
                updateTypes(changedTypes)
            }
        }

        initialized.set(true)
        return Promises.resolve(Unit)
    }

    private fun loadClasspathTypes(): List<TypeDef> {
        val typeArtifacts = localAppService.readStaticLocalArtifacts(
            TypeArtifactHandler.TYPE,
            "json",
            ObjectData.create()
        )
        val result = ArrayList<TypeDef>()
        for (typeArtifact in typeArtifacts) {
            if (typeArtifact !is ObjectData) {
                continue
            }
            val typeDef = typeArtifact.getAs(TypeDef::class.java)?: continue
            if (typeDef.id.isNotBlank()) {
                result.add(typeDef)
            }
        }
        return result
    }

    private fun syncAllTypes(
        registry: MutableEcosRegistry<TypeDef>,
        rawProv: TypesProvider,
        aspectsProv: AspectsProvider
    ) {
        val types = typesService.getAllWithMeta()
        log.info { "Types full sync started for ${types.size} types" }
        val typesToRemove = HashSet(registeredTypes)
        resolver.getResolvedTypesWithMeta(
            types,
            rawProv,
            EmptyProv(),
            aspectsProv,
            Duration.ofHours(1)
        ).forEach {
            registry.setValue(it.entity.id, it)
            registeredTypes.add(it.entity.id)
            typesToRemove.remove(it.entity.id)
        }
        if (typesToRemove.isNotEmpty()) {
            log.info { "Found types to remove: $typesToRemove" }
            for (typeId in typesToRemove) {
                registry.setValue(typeId, null)
            }
        }
        log.info { "Types full sync completed" }
    }

    private inner class AspectsProv : AspectsProvider {
        override fun getAspectInfo(aspectRef: EntityRef): AspectInfo? {
            return aspectsRegistry.getValue(aspectRef.getLocalId())?.getAspectInfo()
        }
    }

    private class EmptyProv : TypesProvider {
        override fun get(id: String): TypeDef? {
            return null
        }
        override fun getChildren(typeId: String): List<String> {
            return emptyList()
        }
    }

    private class RegistryBasedTypesProv(val registry: MutableEcosRegistry<TypeDef>) : TypesProvider {
        override fun get(id: String): TypeDef? {
            return registry.getValue(id)
        }
        override fun getChildren(typeId: String): List<String> {
            return registry.getAllValues()
                .values
                .filter { it.entity.parentRef.getLocalId() == typeId }
                .map { it.entity.id }
        }
    }

    private class TypesServiceBasedProv(
        val typesService: TypesService,
        predefinedTypes: List<TypeDef>
    ) : TypesProvider {

        private val predefinedTypesInfo: Map<String, PredefinedTypeInfo> = predefinedTypes.associate {
            it.id to PredefinedTypeInfo(it)
        }

        private fun isAllAttsFromMapExistsInList(
            attsMap: Map<String, AttributeDef>,
            attsList: List<AttributeDef>
        ): Boolean {
            if (attsMap.isEmpty()) {
                return true
            }
            if (attsList.size < attsMap.size) {
                return false
            }
            var notFoundCount = attsMap.size
            for (att in attsList) {
                if (attsMap.containsKey(att.id)) {
                    if (--notFoundCount == 0) {
                        break
                    }
                }
            }
            return notFoundCount == 0
        }

        override fun get(id: String): TypeDef? {

            val typeFromRepo = typesService.getByIdOrNull(id)
            val predefinedType = predefinedTypesInfo[id]

            if (typeFromRepo == null) {
                return predefinedType?.typeDef
            }
            if (predefinedType == null) {
                return typeFromRepo
            }

            // Add missing predefined attributes to the type model loaded from the database.
            // This is needed to protect against potential bugs when a new attribute is added
            // in artifacts/model/type/... but the database still contains an older version
            // of the type without this attribute.
            // The updated type will eventually be deployed, but until that happens we apply this workaround.
            val repoAtts = typeFromRepo.model.attributes
            val repoSysAtts = typeFromRepo.model.systemAttributes

            val foundMissingAtts = !isAllAttsFromMapExistsInList(predefinedType.attributesById, repoAtts)
            val foundMissingSysAtts = !isAllAttsFromMapExistsInList(predefinedType.sysAttsById, repoSysAtts)

            if (!foundMissingAtts && !foundMissingSysAtts) {
                return typeFromRepo
            }

            val newModel = typeFromRepo.model.copy()

            if (foundMissingAtts) {
                val newAtts = ArrayList(repoAtts)
                for (attribute in predefinedType.attributesById.values) {
                    if (repoAtts.find { it.id == attribute.id } == null) {
                        newAtts.add(attribute)
                    }
                }
                newModel.withAttributes(newAtts)
            }
            if (foundMissingSysAtts) {
                val newSysAtts = ArrayList(repoSysAtts)
                for (attribute in predefinedType.sysAttsById.values) {
                    if (repoSysAtts.find { it.id == attribute.id } == null) {
                        newSysAtts.add(attribute)
                    }
                }
                newModel.withSystemAttributes(newSysAtts)
            }

            return typeFromRepo.copy()
                .withModel(newModel.build())
                .build()
        }

        override fun getChildren(typeId: String): List<String> {
            return typesService.getChildren(typeId)
        }

        private class PredefinedTypeInfo(
            val typeDef: TypeDef
        ) {
            val attributesById: Map<String, AttributeDef> = typeDef.model.attributes.associateBy { it.id }
            val sysAttsById: Map<String, AttributeDef> = typeDef.model.systemAttributes.associateBy { it.id }
        }
    }

    override fun destroy() {
        if (initialized.get()) {
            typesHierarchyUpdater.dispose()
        }
    }

    @Lazy
    @Autowired
    fun setAspectsRegistry(aspectsRegistry: EcosRegistry<AspectDef>) {
        this.aspectsRegistry = aspectsRegistry
    }

    @Autowired
    fun setWebAppApi(webAppApi: EcosWebAppApi) {
        this.webAppApi = webAppApi
    }

    override fun getOrder(): Float {
        return ORDER
    }

    override fun getKey(): String {
        return "ecos-model-app-types"
    }
}
