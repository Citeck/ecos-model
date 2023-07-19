package ru.citeck.ecos.model.domain.endpoint.dto

import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.webapp.api.entity.EntityRef

data class EndpointDto(
    val id: String,
    val name: MLText?,
    val url: String,
    val credentials: EntityRef?
)
