package ru.citeck.ecos.model.domain.workspace.desc

import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef

object WorkspaceDesc {

    const val TYPE_ID = "workspace"

    const val SOURCE_ID = "workspace"

    const val ATT_WORKSPACE_MEMBERS = "workspaceMembers"
    const val ATT_TEMPLATE_REF = "templateRef"
    const val ATT_IS_CURRENT_USER_MANAGER = "isCurrentUserManager"
    const val ATT_IS_CURRENT_USER_MEMBER = "isCurrentUserMember"

    const val DEFAULT_WORKSPACE_ID = "default"

    fun getRef(id: String): EntityRef {
        return EntityRef.create(AppName.EMODEL, SOURCE_ID, id)
    }
}
