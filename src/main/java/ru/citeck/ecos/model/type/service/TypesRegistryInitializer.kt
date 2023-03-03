package ru.citeck.ecos.model.type.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.entity.EntityWithMeta
import ru.citeck.ecos.commons.promise.Promises
import ru.citeck.ecos.model.lib.aspect.dto.AspectInfo
import ru.citeck.ecos.model.type.service.resolver.AspectsProvider
import ru.citeck.ecos.model.type.service.resolver.TypeDefResolver
import ru.citeck.ecos.model.type.service.resolver.TypesProvider
import ru.citeck.ecos.txn.lib.TxnContext
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.promise.Promise
import ru.citeck.ecos.webapp.lib.model.aspect.dto.AspectDef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import ru.citeck.ecos.webapp.lib.registry.EcosRegistry
import ru.citeck.ecos.webapp.lib.registry.EcosRegistryProps
import ru.citeck.ecos.webapp.lib.registry.MutableEcosRegistry
import ru.citeck.ecos.webapp.lib.registry.init.EcosRegistryInitializer

@Component
class TypesRegistryInitializer(
    private val typesService: TypesService
) : EcosRegistryInitializer<TypeDef> {

    companion object {
        const val ORDER = -10f
    }

    private val resolver = TypeDefResolver()

    private lateinit var aspectsRegistry: EcosRegistry<AspectDef>

    override fun init(
        registry: MutableEcosRegistry<TypeDef>,
        values: Map<String, EntityWithMeta<TypeDef>>,
        props: EcosRegistryProps.Initializer
    ): Promise<*> {

        val types = typesService.getAllWithMeta()
        val rawProv = TypesServiceBasedProv(typesService)
        val resProv = RegistryBasedTypesProv(registry)
        val aspectsProv = AspectsProv()

        resolver.getResolvedTypesWithMeta(types, rawProv, EmptyProv(), aspectsProv).forEach {
            registry.setValue(it.entity.id, it)
        }
        typesService.addOnDeletedListener {
            TxnContext.doAfterCommit(0f, false) {
                registry.setValue(it, null)
            }
        }

        fun updateTypes(typeIds: Collection<String>) {
            val key = this::class.java.simpleName + ".types-to-update"
            typeIds.forEach { typeId ->
                TxnContext.processSetAfterCommit(key, typeId) { changedTypes ->
                    resolver.getResolvedTypesWithMeta(
                        typesService.getAllWithMeta(changedTypes),
                        rawProv,
                        resProv,
                        aspectsProv
                    ).forEach {
                        registry.setValue(it.entity.id, it)
                    }
                }
            }
        }

        typesService.addListenerTypeHierarchyChangedListener { changedTypes ->
            updateTypes(changedTypes)
        }

        aspectsRegistry.listenEvents { id, _, _ ->
            val changedTypes = typesService.getAll().filter { rec ->
                rec.aspects.any { it.ref.getLocalId() == id }
            }.map { it.id }
            updateTypes(changedTypes)
        }

        return Promises.resolve(Unit)
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

    private class TypesServiceBasedProv(val typesService: TypesService) : TypesProvider {
        override fun get(id: String): TypeDef? {
            return typesService.getByIdOrNull(id)
        }
        override fun getChildren(typeId: String): List<String> {
            return typesService.getChildren(typeId)
        }
    }

    @Lazy
    @Autowired
    fun setAspectsRegistry(aspectsRegistry: EcosRegistry<AspectDef>) {
        this.aspectsRegistry = aspectsRegistry
    }

    override fun getOrder(): Float {
        return ORDER
    }

    override fun getKey(): String {
        return "ecos-model-app-types"
    }
}
