package ru.citeck.ecos.model.type.service

import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.entity.EntityWithMeta
import ru.citeck.ecos.commons.promise.Promises
import ru.citeck.ecos.model.type.service.resolver.TypeDefResolver
import ru.citeck.ecos.model.type.service.resolver.TypesProvider
import ru.citeck.ecos.webapp.api.promise.Promise
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
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

    override fun init(
        registry: MutableEcosRegistry<TypeDef>,
        values: Map<String, EntityWithMeta<TypeDef>>
    ): Promise<*> {

        val types = typesService.getAllWithMeta()
        val rawProv = TypesServiceBasedProv(typesService)
        val resProv = RegistryBasedProv(registry)

        resolver.getResolvedTypesWithMeta(types, rawProv, EmptyProv()).forEach {
            registry.setValue(it.entity.id, it)
        }
        typesService.addOnDeletedListener {
            registry.setValue(it, null)
        }
        typesService.addListenerTypeHierarchyChangedListener { changedTypes ->
            resolver.getResolvedTypesWithMeta(typesService.getAllWithMeta(changedTypes), rawProv, resProv).forEach {
                registry.setValue(it.entity.id, it)
            }
        }
        return Promises.resolve(Unit)
    }

    private class EmptyProv : TypesProvider {
        override fun get(id: String): TypeDef? {
            return null
        }
        override fun getChildren(typeId: String): List<String> {
            return emptyList()
        }
    }

    private class RegistryBasedProv(val registry: MutableEcosRegistry<TypeDef>) : TypesProvider {
        override fun get(id: String): TypeDef? {
            return registry.getValue(id)
        }
        override fun getChildren(typeId: String): List<String> {
            return registry.getAllValues()
                .values
                .filter { it.entity.parentRef.id == typeId }
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

    override fun getOrder(): Float {
        return ORDER
    }

    override fun getKey(): String {
        return "ecos-model-app-types"
    }
}
