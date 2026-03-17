package ru.citeck.ecos.model.domain.workspace.listener

import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.config.lib.consumer.bean.EcosConfig
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.data.sql.records.dao.atts.DbRecord
import ru.citeck.ecos.data.sql.records.listener.DbRecordChangedEvent
import ru.citeck.ecos.data.sql.records.listener.DbRecordCreatedEvent
import ru.citeck.ecos.data.sql.records.listener.DbRecordsListenerAdapter
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceMemberDesc
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMemberRole
import ru.citeck.ecos.model.domain.workspace.service.EmodelWorkspaceService
import ru.citeck.ecos.model.domain.workspace.utils.WorkspaceSystemIdUtils
import ru.citeck.ecos.model.domain.wstemplate.service.WorkspaceTemplateService
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records2.predicate.model.ValuePredicate
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.entity.toEntityRef
import java.util.*

@Component
class WorkspaceRecordsListener(
    private val wsTemplateService: WorkspaceTemplateService,
    private val emodelWorkspaceService: EmodelWorkspaceService,
    private val recordsService: RecordsService
) : DbRecordsListenerAdapter() {

    companion object {
        private const val WIKI_SOURCE_ID = "${AppName.EMODEL}/wiki"
        private const val WIKI_ROOT_ASSOC = "wikiRoot"

        const val CREATOR_MEMBER_ID = "creator"
        const val ATT_WORKSPACE_MANAGED_BY = "workspaceManagedBy"
    }

    @EcosConfig("wiki-initial-page-content")
    private lateinit var initialPageContent: InitialPageContent

    override fun onCreated(event: DbRecordCreatedEvent) {

        AuthContext.runAsSystem {

            val nestedWorkspaces = recordsService.getAtt(
                event.record,
                WorkspaceDesc.ATT_NESTED_WORKSPACES + "[]?localId"
            ).toList(EntityRef::class.java)

            validateNestedWorkspaces(event.globalRef.getLocalId(), nestedWorkspaces)

            applyTemplate(event)
            createWikiRoot(event.globalRef)
            createCreatorMember(event.globalRef)
            generateAndUpdateSystemId(event.globalRef.getLocalId())
            syncVisibleInWorkspaces(
                event.globalRef,
                added = listOf(
                    recordsService.getAtt(
                        event.record,
                        "$ATT_WORKSPACE_MANAGED_BY?id"
                    ).asText().toEntityRef()
                ),
                removed = emptyList()
            )
        }
    }

    fun generateAndUpdateSystemId(workspaceId: String) {

        val workspaceRef = WorkspaceDesc.getRef(workspaceId)

        val workspaceSystemId = WorkspaceSystemIdUtils.createId(workspaceId) { wsIdToCheck ->
            recordsService.query(
                RecordsQuery.create()
                    .withSourceId(WorkspaceDesc.SOURCE_ID)
                    .withQuery(
                        Predicates.and(
                            Predicates.notEq("id", workspaceId),
                            Predicates.eq(WorkspaceDesc.ATT_SYSTEM_ID, wsIdToCheck)
                        )
                    ).withMaxItems(1).build()
            ).getRecords().isNotEmpty()
        }

        recordsService.mutateAtt(workspaceRef, WorkspaceDesc.ATT_SYSTEM_ID, workspaceSystemId)
    }

    override fun onChanged(event: DbRecordChangedEvent) {
        event.assocs.find {
            it.assocId == WorkspaceDesc.ATT_NESTED_WORKSPACES
        }?.let {
            validateNestedWorkspaces(event.globalRef.getLocalId(), it.added)
        }
        val wsManagedByDiff = event.systemAssocs.find { it.assocId == ATT_WORKSPACE_MANAGED_BY }
        if (wsManagedByDiff != null) {
            syncVisibleInWorkspaces(event.globalRef, added = wsManagedByDiff.added, removed = wsManagedByDiff.removed)
        }
    }

    private fun syncVisibleInWorkspaces(
        workspaceRef: EntityRef,
        added: List<EntityRef>,
        removed: List<EntityRef>
    ) {
        fun updateWsVisibility(forRef: EntityRef, add: Boolean) {
            if (forRef.isEmpty()) return
            val mutAttPrefix = if (add) "att_add_" else "att_rem_"
            recordsService.mutateAtt(
                forRef,
                mutAttPrefix + DbRecord.ATT_VISIBLE_IN_WORKSPACES,
                workspaceRef
            )
        }
        added.forEach { updateWsVisibility(it, true) }
        removed.forEach { updateWsVisibility(it, false) }
    }

    private fun validateNestedWorkspaces(workspaceId: String, addedNestedWorkspaces: List<EntityRef>) {
        if (addedNestedWorkspaces.isEmpty()) {
            return
        }
        val nestedWorkspacesIds = addedNestedWorkspaces.map { it.getLocalId() }
        if (nestedWorkspacesIds.contains(workspaceId)) {
            error("The current workspace cannot be selected as a nested workspace.")
        }
        val isCurrentWorkspaceNestedInOther = recordsService.query(
            RecordsQuery.create()
                .withSourceId(WorkspaceDesc.SOURCE_ID)
                .withQuery(ValuePredicate.contains(WorkspaceDesc.ATT_NESTED_WORKSPACES, WorkspaceDesc.getRef(workspaceId)))
                .withMaxItems(1)
                .build()
        ).getRecords().isNotEmpty()
        if (isCurrentWorkspaceNestedInOther) {
            error("The current workspace is already nested within another workspace. Deep nesting is not supported.")
        }

        val nestedWithNestedWorkspaces = emodelWorkspaceService.getNestedWorkspaces(nestedWorkspacesIds)
            .mapIndexedNotNull { idx, nested ->
                if (nested.isNotEmpty()) {
                    nestedWorkspacesIds[idx]
                } else {
                    null
                }
            }
        if (nestedWithNestedWorkspaces.isNotEmpty()) {
            error(
                "The following workspaces cannot be added as nested workspaces, " +
                    "because they already contain their own nested workspaces: " +
                    nestedWithNestedWorkspaces.joinToString()
            )
        }
    }

    fun createCreatorMember(workspaceRef: EntityRef) {
        if (AuthContext.isSystemAuth(AuthContext.getCurrentFullAuth())) {
            return
        }
        recordsService.create(
            WorkspaceMemberDesc.SOURCE_ID,
            mapOf(
                WorkspaceMemberDesc.ATT_MEMBER_ID to CREATOR_MEMBER_ID,
                RecordConstants.ATT_PARENT to workspaceRef,
                RecordConstants.ATT_PARENT_ATT to WorkspaceDesc.ATT_WORKSPACE_MEMBERS,
                WorkspaceMemberDesc.ATT_AUTHORITIES to AuthorityType.PERSON.getRef(AuthContext.getCurrentUser()),
                WorkspaceMemberDesc.ATT_MEMBER_ROLE to WorkspaceMemberRole.MANAGER
            )
        )
    }

    fun createWikiRoot(workspaceRef: EntityRef) {
        val wsId = workspaceRef.getLocalId()
        val userLocale = Locale.of(I18nContext.getLocale().language)
        recordsService.create(
            WIKI_SOURCE_ID,
            mapOf(
                "id" to "$wsId\$ROOT",
                "title" to initialPageContent.title.getClosest(userLocale),
                "text" to initialPageContent.text.getClosest(userLocale),
                RecordConstants.ATT_PARENT to workspaceRef,
                RecordConstants.ATT_PARENT_ATT to WIKI_ROOT_ASSOC,
                RecordConstants.ATT_WORKSPACE to wsId
            )
        )
    }

    private fun applyTemplate(event: DbRecordCreatedEvent) {

        val templateRef = recordsService.getAtt(
            event.record,
            WorkspaceDesc.ATT_TEMPLATE_REF + ScalarType.ID_SCHEMA
        ).asText().toEntityRef()

        if (templateRef.isEmpty()) {
            return
        }

        val wsId = event.globalRef.getLocalId()
        wsTemplateService.deployArtifactsForWorkspace(wsId, templateRef)
    }

    class InitialPageContent(
        val title: MLText,
        val text: MLText
    )
}
