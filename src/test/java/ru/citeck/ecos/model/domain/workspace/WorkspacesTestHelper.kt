package ru.citeck.ecos.model.domain.workspace

import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.lib.workspace.USER_WORKSPACE_PREFIX
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef

fun String.toWorkspaceRef(): EntityRef {
    return EntityRef.create(AppName.EMODEL, WorkspaceDesc.SOURCE_ID, this)
}

fun String.toUsernameToUserVirtualWorkspaceRef(): EntityRef {
    return EntityRef.create(AppName.EMODEL, WorkspaceDesc.SOURCE_ID, "$USER_WORKSPACE_PREFIX$this")
}
