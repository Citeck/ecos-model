package ru.citeck.ecos.model.domain.workspace.listener

import org.springframework.stereotype.Component
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.type.RecordChangedEvent
import ru.citeck.ecos.events2.type.RecordCreatedEvent
import ru.citeck.ecos.events2.type.RecordDeletedEvent
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityConstants
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceMemberDesc
import ru.citeck.ecos.model.domain.workspace.service.CustomWorkspaceApi
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Component
class EmodelWorkspaceCacheEvictor(
    eventsService: EventsService,
    private val recordsService: RecordsService,
    private val customWorkspaceApi: CustomWorkspaceApi
) {

    init {
        val ctxAttCondition = Predicates.notEq(WorkspaceDesc.CTX_ATT_DEPLOY_WORKSPACE_BOOL, true)
        val wsPredicate = Predicates.and(
            Predicates.eq("typeDef.id", WorkspaceDesc.TYPE_ID),
            ctxAttCondition
        )
        val wsMemberPredicate = Predicates.and(
            Predicates.eq("typeDef.id", WorkspaceMemberDesc.TYPE_ID),
            ctxAttCondition
        )

        eventsService.addListener<WorkspaceCreated> {
            withTransactional(true)
            withEventType(RecordCreatedEvent.TYPE)
            withDataClass(WorkspaceCreated::class.java)
            withFilter(wsPredicate)
            withAction { onWorkspaceCreated(it) }
        }

        eventsService.addListener<WorkspaceChanged> {
            withTransactional(true)
            withEventType(RecordChangedEvent.TYPE)
            withDataClass(WorkspaceChanged::class.java)
            withFilter(
                Predicates.and(
                    wsPredicate,
                    Predicates.eq("diff._has.workspaceMembers?bool", true)
                )
            )
            withAction { onWorkspaceChanged(it) }
        }

        eventsService.addListener<WorkspaceMemberChanged> {
            withTransactional(true)
            withEventType(RecordChangedEvent.TYPE)
            withDataClass(WorkspaceMemberChanged::class.java)
            withFilter(
                Predicates.and(
                    wsMemberPredicate,
                    Predicates.or(
                        Predicates.eq("diff._has.${WorkspaceMemberDesc.ATT_AUTHORITIES}?bool", true),
                        Predicates.eq("diff._has.${WorkspaceMemberDesc.ATT_MEMBER_ROLE}?bool", true)
                    )
                )
            )
            withAction { onWorkspaceMemberChanged(it) }
        }

        eventsService.addListener<WorkspaceMemberDeleted> {
            withTransactional(true)
            withEventType(RecordDeletedEvent.TYPE)
            withDataClass(WorkspaceMemberDeleted::class.java)
            withFilter(wsMemberPredicate)
            withAction { onWorkspaceMemberDeleted(it) }
        }

        eventsService.addListener<PersonGroupsChanged> {
            withTransactional(true)
            withEventType(RecordChangedEvent.TYPE)
            withDataClass(PersonGroupsChanged::class.java)
            withFilter(
                Predicates.and(
                    Predicates.eq("typeDef.id", AuthorityType.PERSON.sourceId),
                    Predicates.eq("diff._has.${AuthorityConstants.ATT_AUTHORITY_GROUPS}?bool", true)
                )
            )
            withAction { onPersonGroupsChanged(it) }
        }

        eventsService.addListener<GroupGroupsChanged> {
            withTransactional(true)
            withEventType(RecordChangedEvent.TYPE)
            withDataClass(GroupGroupsChanged::class.java)
            withFilter(
                Predicates.and(
                    Predicates.eq("typeDef.id", AuthorityType.GROUP.sourceId),
                    Predicates.eq("diff._has.${AuthorityConstants.ATT_AUTHORITY_GROUPS}?bool", true)
                )
            )
            withAction { onGroupGroupsChanged(it) }
        }
    }

    private fun onWorkspaceCreated(event: WorkspaceCreated) {
        event.members.flatMap { it.authorities }.forEach {
            customWorkspaceApi.evictUserWorkspacesForAuthority(it)
        }
    }

    private fun onWorkspaceChanged(event: WorkspaceChanged) {
        val workspaceId = event.record.getLocalId()
        evictWorkspaceScoped(workspaceId)

        val diff = event.changed.find { it.id == WorkspaceDesc.ATT_WORKSPACE_MEMBERS } ?: return

        val memberRefs = HashSet<EntityRef>()
        memberRefs.addAll(diff.added)
        memberRefs.addAll(diff.removed)
        if (memberRefs.isEmpty()) {
            return
        }

        val members = recordsService.getAtts(memberRefs, MemberAuthorities::class.java)
        members.flatMap { it.authorities }.forEach {
            customWorkspaceApi.evictUserWorkspacesForAuthority(it)
        }
    }

    private fun onWorkspaceMemberChanged(event: WorkspaceMemberChanged) {
        evictWorkspaceScoped(event.workspaceRef.getLocalId())

        val diff = event.changed.find { it.id == WorkspaceMemberDesc.ATT_AUTHORITIES } ?: return

        (diff.added + diff.removed).forEach {
            customWorkspaceApi.evictUserWorkspacesForAuthority(it)
        }
    }

    private fun onWorkspaceMemberDeleted(event: WorkspaceMemberDeleted) {
        evictWorkspaceScoped(event.workspaceRef.getLocalId())
        event.authorities.forEach {
            customWorkspaceApi.evictUserWorkspacesForAuthority(it)
        }
    }

    private fun onPersonGroupsChanged(event: PersonGroupsChanged) {
        val userId = event.record.getLocalId()
        customWorkspaceApi.evictUserWorkspaces(userId)
        customWorkspaceApi.evictIsUserManagerOfForUser(userId)
    }

    private fun onGroupGroupsChanged(event: GroupGroupsChanged) {
        customWorkspaceApi.evictAllUserWorkspaces()
        customWorkspaceApi.evictAllIsUserManagerOf()
    }

    private fun evictWorkspaceScoped(workspaceId: String) {
        customWorkspaceApi.evictWorkspaceManagersRefs(workspaceId)
        customWorkspaceApi.evictIsUserManagerOfForWorkspace(workspaceId)
    }

    private data class WorkspaceCreated(
        val record: EntityRef,
        @AttName("record.workspaceMembers[]?json")
        val members: List<MemberAuthorities>
    )

    private data class WorkspaceChanged(
        val record: EntityRef,
        @AttName("diff.list[]?json")
        val changed: List<DiffData>
    )

    private data class WorkspaceMemberChanged(
        @AttName("diff.list[]?json")
        val changed: List<DiffData>,
        @AttName("record._parent?id")
        val workspaceRef: EntityRef
    )

    private data class WorkspaceMemberDeleted(
        @AttName("record.authorities[]?id")
        val authorities: List<EntityRef>,
        @AttName("record._parent?id")
        val workspaceRef: EntityRef
    )

    private data class PersonGroupsChanged(
        val record: EntityRef
    )

    private data class GroupGroupsChanged(
        val record: EntityRef
    )

    private data class MemberAuthorities(
        val authorities: List<EntityRef> = emptyList()
    )

    private data class DiffData(
        val id: String,
        val added: Set<EntityRef> = emptySet(),
        val removed: Set<EntityRef> = emptySet()
    )
}
