package ru.citeck.ecos.model.domain.perms.service

import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.entity.EntityWithMeta
import ru.citeck.ecos.model.lib.type.dto.TypePermsDef
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.webapp.api.promise.Promise
import ru.citeck.ecos.webapp.api.promise.Promises
import ru.citeck.ecos.webapp.lib.registry.EcosRegistryProps
import ru.citeck.ecos.webapp.lib.registry.MutableEcosRegistry
import ru.citeck.ecos.webapp.lib.registry.init.EcosRegistryInitializer

@Component
class TypePermsInitializer(
    private val typePermsService: TypePermsService,
    private val workspaceService: WorkspaceService
) : EcosRegistryInitializer<TypePermsDef> {

    companion object {
        const val ORDER = -10f
    }

    override fun init(
        registry: MutableEcosRegistry<TypePermsDef>,
        values: Map<String, EntityWithMeta<TypePermsDef>>,
        props: EcosRegistryProps.Initializer
    ): Promise<*> {
        typePermsService.migrateLegacyPermissions()
        typePermsService.allWithMeta.forEach {
            registry.setValue(workspaceService.convertToStrId(it.id), it.asRegistryValue())
        }
        typePermsService.addListener { before, after ->
            // Only emodel publishes matrices; quarantined rows never enter the shared registry.
            val previous = before?.takeIf { typePermsService.isApplicable(it) }
            val current = after?.takeIf { typePermsService.isApplicable(it) }
            val previousKey = previous?.let { workspaceService.convertToStrId(it.id) }
            val currentKey = current?.let { workspaceService.convertToStrId(it.id) }
            if (!previousKey.isNullOrBlank() && previousKey != currentKey) {
                registry.setValue(previousKey, null)
            }
            if (!currentKey.isNullOrBlank()) {
                registry.setValue(currentKey, current?.asRegistryValue())
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
