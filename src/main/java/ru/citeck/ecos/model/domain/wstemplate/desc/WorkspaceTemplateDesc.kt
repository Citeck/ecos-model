package ru.citeck.ecos.model.domain.wstemplate.desc

import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef

object WorkspaceTemplateDesc {

    const val TYPE_ID = "workspace-template"

    const val SOURCE_ID = "workspace-template"

    const val ATT_WORKSPACE_REF = "workspaceRef"
    const val ATT_ARTIFACTS = "artifacts"

    fun getRef(id: String): EntityRef {
        return EntityRef.create(AppName.EMODEL, SOURCE_ID, id)
    }
}
