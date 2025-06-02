package ru.citeck.ecos.model.domain.workspace.patch

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthUser
import ru.citeck.ecos.data.sql.records.DbRecordsControlAtts
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceMemberDesc
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMemberRole
import ru.citeck.ecos.model.domain.workspace.listener.WorkspaceRecordsListener
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.patch.annotaion.EcosLocalPatch
import java.util.concurrent.Callable

@Component
@EcosLocalPatch("move-creator-to-workspace-members", "2025-04-18T00:00:00Z")
class MoveCreatorToWorkspaceMembers(
    val recordsService: RecordsService
) : Callable<Any> {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun call(): Any {

        val workspacesToMigrate = recordsService.query(
            RecordsQuery.create()
                .withSourceId(WorkspaceDesc.SOURCE_ID)
                .withQuery(
                    Predicates.and(
                        Predicates.notEq(RecordConstants.ATT_CREATOR, AuthorityType.PERSON.getRef(AuthUser.SYSTEM)),
                        Predicates.notContains(
                            "workspaceMembers.memberId",
                            WorkspaceRecordsListener.CREATOR_MEMBER_ID
                        )
                    )
                )
                .withMaxItems(10000)
                .build(),
            WorkspaceAtts::class.java
        )

        log.info { "Workspaces to migrate: ${workspacesToMigrate.getRecords().size}" }

        for (workspace in workspacesToMigrate.getRecords()) {
            recordsService.create(
                WorkspaceMemberDesc.SOURCE_ID,
                mapOf(
                    WorkspaceMemberDesc.ATT_MEMBER_ID to WorkspaceRecordsListener.CREATOR_MEMBER_ID,
                    RecordConstants.ATT_PARENT to workspace.id,
                    RecordConstants.ATT_PARENT_ATT to WorkspaceDesc.ATT_WORKSPACE_MEMBERS,
                    WorkspaceMemberDesc.ATT_AUTHORITIES to workspace.creator,
                    WorkspaceMemberDesc.ATT_MEMBER_ROLE to WorkspaceMemberRole.MANAGER,
                    DbRecordsControlAtts.DISABLE_EVENTS to true
                )
            )
        }

        return workspacesToMigrate.getRecords().map { it.id.getLocalId() }
    }

    private class WorkspaceAtts(
        val id: EntityRef,
        @AttName(RecordConstants.ATT_CREATOR)
        val creator: EntityRef
    )
}
