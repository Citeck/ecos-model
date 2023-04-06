package ru.citeck.ecos.model.domain.doceditor.onlyoffice.api

import ru.citeck.ecos.commons.json.serialization.annotation.IncludeNonDefault
import ru.citeck.ecos.webapp.api.entity.EntityRef

@IncludeNonDefault
data class OnlyOfficeDocEditorCallbackJwt(
    val ref: EntityRef = EntityRef.EMPTY,
    val att: String = ""
)
