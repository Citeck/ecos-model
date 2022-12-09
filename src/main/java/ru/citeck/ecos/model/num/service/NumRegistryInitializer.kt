package ru.citeck.ecos.model.num.service

import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.entity.EntityWithMeta
import ru.citeck.ecos.commons.promise.Promises
import ru.citeck.ecos.model.lib.num.dto.NumTemplateDef
import ru.citeck.ecos.webapp.api.promise.Promise
import ru.citeck.ecos.webapp.lib.registry.EcosRegistryProps
import ru.citeck.ecos.webapp.lib.registry.MutableEcosRegistry
import ru.citeck.ecos.webapp.lib.registry.init.EcosRegistryInitializer

@Component
class NumRegistryInitializer(
    private val numTemplateService: NumTemplateService
) : EcosRegistryInitializer<NumTemplateDef> {

    companion object {
        const val ORDER = -10f
    }

    override fun init(
        registry: MutableEcosRegistry<NumTemplateDef>,
        values: Map<String, EntityWithMeta<NumTemplateDef>>,
        props: EcosRegistryProps.Initializer
    ): Promise<*> {
        numTemplateService.all.forEach {
            registry.setValue(it.entity.id, it)
        }
        numTemplateService.addListener { before, after ->
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
