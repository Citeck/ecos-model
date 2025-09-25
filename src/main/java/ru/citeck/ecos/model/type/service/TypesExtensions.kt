package ru.citeck.ecos.model.type.service

import ru.citeck.ecos.model.lib.workspace.IdInWs
import ru.citeck.ecos.model.type.repository.TypeEntity
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef

fun TypeDef.getTypeId(): IdInWs {
    return IdInWs.create(this.workspace, this.id)
}

fun TypeEntity.getTypeId(): IdInWs {
    return IdInWs.create(this.workspace, this.extId)
}
