package ru.citeck.ecos.model.num.service

import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.entity.EntityWithMeta
import ru.citeck.ecos.model.lib.num.dto.NumTemplateDef
import ru.citeck.ecos.webapp.api.promise.Promise
import ru.citeck.ecos.webapp.api.promise.Promises
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
        numTemplateService.getAll().forEach {
            registry.setValue(getGlobalId(it), it)
        }
        numTemplateService.addListener { before, after ->
            val key = if (before?.entity?.id.isNullOrBlank()) {
                getGlobalId(after)
            } else {
                getGlobalId(before)
            }
            if (key.isNotBlank()) {
                registry.setValue(key, after)
            }
        }
        return Promises.resolve(Unit)
    }

    private fun getGlobalId(entity: EntityWithMeta<NumTemplateDef>?): String {
        entity ?: return ""
        if (entity.entity.id.isBlank()) {
            return ""
        }
        return if (entity.entity.workspace.isEmpty()) {
            entity.entity.id
        } else {
            entity.entity.workspace + ":" + entity.entity.id
        }
    }

    override fun getOrder(): Float {
        return ORDER
    }

    override fun getKey(): String {
        return "ecos-model-app-templates"
    }
}
