package ru.citeck.ecos.model.domain.workspace.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.records3.record.dao.impl.proxy.RecordsDaoProxy

@Component
class WorkspaceMemberProxyDao : RecordsDaoProxy(
    WORKSPACE_MEMBER_SOURCE_ID,
    WORKSPACE_MEMBER_REPO_SOURCE_ID
) {

    companion object {
        const val WORKSPACE_MEMBER_SOURCE_ID = "workspace-member"
        const val WORKSPACE_MEMBER_REPO_SOURCE_ID = "$WORKSPACE_MEMBER_SOURCE_ID-repo"
    }
}
