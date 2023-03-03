package ru.citeck.ecos.model.type.service.resolver

import ru.citeck.ecos.model.lib.aspect.dto.AspectInfo
import ru.citeck.ecos.webapp.api.entity.EntityRef

interface AspectsProvider {

    fun getAspectInfo(aspectRef: EntityRef): AspectInfo?
}
