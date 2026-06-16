package ru.citeck.ecos.model.type.service.resolver

import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import ru.citeck.ecos.model.lib.aspect.dto.AspectInfo
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.aspect.dto.AspectDef
import ru.citeck.ecos.webapp.lib.registry.EcosRegistry

@Component
class RegistryBasedAspectsProvider(
    @Lazy private val aspectsRegistry: EcosRegistry<AspectDef>
) : AspectsProvider {

    override fun getAspectInfo(aspectRef: EntityRef): AspectInfo? {
        return aspectsRegistry.getValue(aspectRef.getLocalId())?.getAspectInfo()
    }
}
