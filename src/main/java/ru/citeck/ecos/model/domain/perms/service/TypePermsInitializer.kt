package ru.citeck.ecos.model.domain.perms.service

import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.entity.EntityWithMeta
import ru.citeck.ecos.commons.promise.Promises
import ru.citeck.ecos.model.lib.type.dto.TypePermsDef
import ru.citeck.ecos.webapp.api.promise.Promise
import ru.citeck.ecos.webapp.lib.registry.EcosRegistryProps
import ru.citeck.ecos.webapp.lib.registry.MutableEcosRegistry
import ru.citeck.ecos.webapp.lib.registry.init.EcosRegistryInitializer

@Component
class TypePermsInitializer(
    private val typePermsService: TypePermsService
) : EcosRegistryInitializer<TypePermsDef> {

    companion object {
        const val ORDER = -10f
    }

    override fun init(
        registry: MutableEcosRegistry<TypePermsDef>,
        values: Map<String, EntityWithMeta<TypePermsDef>>,
        props: EcosRegistryProps.Initializer
    ): Promise<*> {
        typePermsService.allWithMeta.forEach {
            registry.setValue(it.entity.id, it)
        }
        typePermsService.addListener { before, after ->
            val key = before?.entity?.id ?: after?.entity?.id ?: ""
            if (key.isNotBlank()) {
                registry.setValue(key, after)
            }
        }
        return Promises.resolve(Unit)
    }

    override fun getOrder(): Float {
        return ORDER
    }

    override fun getKey(): String {
        return "ecos-model-app-templates"
    }
}
