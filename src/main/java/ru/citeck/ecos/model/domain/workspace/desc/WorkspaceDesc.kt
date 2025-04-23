package ru.citeck.ecos.model.domain.workspace.desc

import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef

object WorkspaceDesc {

    const val TYPE_ID = "workspace"

    const val SOURCE_ID = "workspace"

    const val ATT_ID = "id"
    const val ATT_NAME = "name"
    const val ATT_DESCRIPTION = "description"
    const val ATT_WORKSPACE_MEMBERS = "workspaceMembers"
    const val ATT_DEFAULT_WORKSPACE_MEMBERS = "defaultWorkspaceMembers"
    const val ATT_TEMPLATE_REF = "templateRef"
    const val ATT_SYSTEM = "system"
    const val ATT_SYSTEM_BOOL = "$ATT_SYSTEM?bool"
    const val ATT_IS_CURRENT_USER_MANAGER = "isCurrentUserManager"
    const val ATT_IS_CURRENT_USER_MEMBER = "isCurrentUserMember"
    const val ATT_IS_CURRENT_USER_DIRECT_MEMBER = "isCurrentUserDirectMember"
    const val ATT_IS_CURRENT_USER_LAST_MANAGER = "isCurrentUserLastManager"

    const val CTX_ATT_DEPLOY_WORKSPACE = "deployWorkspace"
    const val CTX_ATT_DEPLOY_WORKSPACE_BOOL = "\$$CTX_ATT_DEPLOY_WORKSPACE?bool"

    const val DEFAULT_WORKSPACE_ID = "default"

    fun getRef(id: String): EntityRef {
        return EntityRef.create(AppName.EMODEL, SOURCE_ID, id)
    }
}
