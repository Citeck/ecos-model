package ru.citeck.ecos.model.domain.workspace.desc

import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef

object WorkspaceMemberDesc {

    const val TYPE_ID = "workspace-member"

    const val SOURCE_ID = "workspace-member"

    const val ATT_MEMBER_ID = "memberId"
    const val ATT_AUTHORITY = "authority"
    const val ATT_MEMBER_ROLE = "memberRole"

    fun getRef(id: String): EntityRef {
        return EntityRef.create(AppName.EMODEL, SOURCE_ID, id)
    }
}
