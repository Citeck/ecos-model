package ru.citeck.ecos.model.domain.workspace.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceMemberDesc
import ru.citeck.ecos.records3.record.dao.impl.proxy.RecordsDaoProxy

@Component
class WorkspaceMemberProxyDao :
    RecordsDaoProxy(
        WorkspaceMemberDesc.SOURCE_ID,
        WORKSPACE_MEMBER_REPO_SOURCE_ID
    ) {

    companion object {
        const val WORKSPACE_MEMBER_REPO_SOURCE_ID = "${WorkspaceMemberDesc.SOURCE_ID}-repo"
    }
}
