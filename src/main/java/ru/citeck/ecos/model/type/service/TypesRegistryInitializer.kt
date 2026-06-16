package ru.citeck.ecos.model.type.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.entity.EntityWithMeta
import ru.citeck.ecos.model.lib.workspace.*
import ru.citeck.ecos.model.type.service.resolver.AspectsProvider
import ru.citeck.ecos.model.type.service.resolver.TypeDefResolver
import ru.citeck.ecos.model.type.service.resolver.TypesProvider
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.txn.lib.TxnContext
import ru.citeck.ecos.webapp.api.EcosWebAppApi
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
@DependsOn("workspaceRepoDao")
class TypesRegistryInitializer(
    private val typesService: TypesService,
    private val resolver: TypeDefResolver,
    private val rawTypesProvider: TypesProvider,
    private val aspectsProvider: AspectsProvider,
    private val workspaceService: WorkspaceService? = null
) : EcosRegistryInitializer<TypeDef>,
    DisposableBean {

    companion object {
        const val ORDER = -10f

        private val log = KotlinLogging.logger {}
    }

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

        val rawProv = rawTypesProvider
        val resProv = RegistryBasedTypesProv(registry)
        val aspectsProv = aspectsProvider

        typesHierarchyUpdater = TypesHierarchyUpdater(
            typesService,
            resolver,
            rawProv,
            resProv,
            aspectsProv,
            registry,
            webAppApi,
            workspaceService
        ) {
            syncAllTypes(registry, rawProv, aspectsProv, FullSyncType.ALL)
        }

        syncAllTypes(registry, rawProv, aspectsProv, FullSyncType.WITHOUT_WS)
        webAppApi.doBeforeAppReady {
            syncAllTypes(registry, rawProv, aspectsProv, FullSyncType.WITH_WS)
        }
        typesHierarchyUpdater.start()

        typesService.addOnDeletedListener {
            TxnContext.doAfterCommit(0f, false) {
                val idInWs = IdInWs.create(it.entity.workspace, it.entity.id)
                registry.setValue(workspaceService.convertToStrIdSafe(idInWs), null)
            }
        }

        fun updateTypes(typeIds: Collection<IdInWs>) {
            val typesSet = if (typeIds !is Set<IdInWs>) typeIds.toSet() else typeIds
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
                }.map { it.getTypeId() }
                updateTypes(changedTypes)
            }
        }

        initialized.set(true)
        return Promises.resolve(Unit)
    }

    private fun syncAllTypes(
        registry: MutableEcosRegistry<TypeDef>,
        rawProv: TypesProvider,
        aspectsProv: AspectsProvider,
        syncType: FullSyncType
    ) {
        val predicate = when (syncType) {
            FullSyncType.ALL -> Predicates.alwaysTrue()
            FullSyncType.WITHOUT_WS -> Predicates.empty("workspace")
            FullSyncType.WITH_WS -> Predicates.notEmpty("workspace")
        }
        val types = typesService.getAllWithMeta(100_000, 0, predicate, listOf())
        log.info { "Types full sync started for ${types.size} types" }
        val typesToRemove = HashSet(registeredTypes)
        if (syncType != FullSyncType.ALL) {
            val typesToRemoveIt = typesToRemove.iterator()
            while (typesToRemoveIt.hasNext()) {
                if (!syncType.isMatch(typesToRemoveIt.next())) {
                    typesToRemoveIt.remove()
                }
            }
        }
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

    private enum class FullSyncType(
        val isMatch: (String) -> Boolean
    ) {
        ALL({ true }),
        WITHOUT_WS({ !it.contains(IdInWs.WS_DELIM) }),
        WITH_WS({ it.contains(IdInWs.WS_DELIM) })
    }
}
