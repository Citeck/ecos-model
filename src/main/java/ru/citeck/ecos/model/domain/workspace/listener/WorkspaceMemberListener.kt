package ru.citeck.ecos.model.domain.workspace.listener

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.type.RecordChangedEvent
import ru.citeck.ecos.events2.type.RecordCreatedEvent
import ru.citeck.ecos.events2.type.RecordDeletedEvent
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityConstants
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceMemberDesc
import ru.citeck.ecos.model.domain.workspace.listener.WorkspaceRecordsListener.Companion.CREATOR_MEMBER_ID
import ru.citeck.ecos.model.domain.workspace.service.EmodelWorkspaceService.Companion.USER_JOIN_PREFIX
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.notifications.lib.Notification
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.lib.service.NotificationService
import ru.citeck.ecos.records2.predicate.model.AndPredicate
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Component
class WorkspaceMemberListener(
    eventsService: EventsService,
    private val recordsService: RecordsService,
    private val notificationService: NotificationService
) {

    companion object {
        private const val ATT_IS_EXTERNAL_USER = "authorities._has.GROUP_EXTERNAL_USERS?bool!"

        private var log = KotlinLogging.logger {}
    }

    private val addMemberNotificationTemplate = EntityRef.create(
        AppName.NOTIFICATIONS,
        "template",
        "workspace-add-member-notification"
    )

    private val removeMemberNotificationTemplate = EntityRef.create(
        AppName.NOTIFICATIONS,
        "template",
        "workspace-remove-member-notification"
    )

    init {
        val ctxAttCondition = Predicates.notEq(WorkspaceDesc.CTX_ATT_DEPLOY_WORKSPACE_BOOL, true)
        val commonWsPredicate = AndPredicate.of(
            Predicates.eq("typeDef.id", WorkspaceDesc.TYPE_ID),
            ctxAttCondition
        )
        val commonWsMemberPredicate = AndPredicate.of(
            Predicates.eq("typeDef.id", WorkspaceMemberDesc.TYPE_ID),
            ctxAttCondition
        )
        eventsService.addListener<WorkspaceCreated> {
            withTransactional(true)
            withEventType(RecordCreatedEvent.TYPE)
            withDataClass(WorkspaceCreated::class.java)
            withFilter(commonWsPredicate)
            withAction { sendNotificationWhenWorkspaceCreated(it) }
        }

        eventsService.addListener<WorkspaceChanged> {
            withTransactional(true)
            withEventType(RecordChangedEvent.TYPE)
            withDataClass(WorkspaceChanged::class.java)
            withFilter(
                Predicates.and(
                    commonWsPredicate,
                    Predicates.eq("diff._has.workspaceMembers?bool", true)
                )
            )
            withAction { sendNotificationWhenWorkspaceChanged(it) }
        }

        eventsService.addListener<WorkspaceMemberChanged> {
            withTransactional(true)
            withEventType(RecordChangedEvent.TYPE)
            withDataClass(WorkspaceMemberChanged::class.java)
            withFilter(
                Predicates.and(
                    commonWsMemberPredicate,
                    Predicates.eq("diff._has.authorities?bool", true)
                )
            )
            withAction { sendNotificationWhenWorkspaceMemberChanged(it) }
        }

        eventsService.addListener<WorkspaceMemberDeleted> {
            withTransactional(true)
            withEventType(RecordDeletedEvent.TYPE)
            withDataClass(WorkspaceMemberDeleted::class.java)
            withFilter(commonWsMemberPredicate)
            withAction { sendNotificationWhenWorkspaceMemberDeleted(it) }
        }
    }

    private fun sendNotificationWhenWorkspaceCreated(workspaceCreated: WorkspaceCreated) {
        val authorities = workspaceCreated.members.flatMapTo(HashSet()) { it.authorities }
        sendNotification(workspaceCreated.record, authorities, addMemberNotificationTemplate)
    }

    private fun sendNotificationWhenWorkspaceChanged(workspaceChanged: WorkspaceChanged) {
        val workspaceMembersDiff = workspaceChanged.changed.find { it.id == "workspaceMembers" }
        if (workspaceMembersDiff == null) {
            log.error { "WorkspaceMembers diff not found" }
            return
        }

        if (workspaceMembersDiff.added.isNotEmpty()) {
            val members = recordsService.getAtts(workspaceMembersDiff.added, WorkspaceMember::class.java)
            val authorities = members.filter {
                !it.isUserJoin() && !it.isCreator()
            }.flatMapTo(HashSet()) {
                it.authorities
            }
            sendNotification(workspaceChanged.record, authorities, addMemberNotificationTemplate)
        }
    }

    private fun sendNotificationWhenWorkspaceMemberChanged(workspaceMemberChanged: WorkspaceMemberChanged) {
        val authoritiesDiff = workspaceMemberChanged.changed.find { it.id == "authorities" }
        if (authoritiesDiff == null) {
            log.error { "Authorities diff not found" }
            return
        }

        if (authoritiesDiff.added.isNotEmpty()) {
            sendNotification(
                workspaceMemberChanged.workspaceRef,
                authoritiesDiff.added,
                addMemberNotificationTemplate
            )
        }

        if (authoritiesDiff.removed.isNotEmpty()) {
            sendNotification(
                workspaceMemberChanged.workspaceRef,
                authoritiesDiff.removed,
                removeMemberNotificationTemplate
            )
        }
    }

    private fun sendNotificationWhenWorkspaceMemberDeleted(workspaceMemberDeleted: WorkspaceMemberDeleted) {
        val currentUser = AuthorityType.PERSON.getRef(AuthContext.getCurrentUser())
        if ((workspaceMemberDeleted.member.isUserJoin() || workspaceMemberDeleted.member.isCreator()) &&
            workspaceMemberDeleted.member.authorities.size == 1 &&
            workspaceMemberDeleted.member.authorities.first() == currentUser
        ) {
            return
        }

        sendNotification(
            workspaceMemberDeleted.workspaceRef,
            workspaceMemberDeleted.member.authorities,
            removeMemberNotificationTemplate
        )
    }

    private fun sendNotification(workspaceRef: EntityRef, authorities: Set<EntityRef>, templateRef: EntityRef) {
        val recipients = getEmailsFromAuthorities(authorities)
        if (recipients.isNotEmpty()) {
            notificationService.send(
                Notification.Builder()
                    .recipients(recipients)
                    .record(workspaceRef)
                    .notificationType(NotificationType.EMAIL_NOTIFICATION)
                    .templateRef(templateRef)
                    .build()
            )
        }
    }

    private fun getEmailsFromAuthorities(authorities: Set<EntityRef>): List<String> {
        if (authorities.isEmpty()) {
            return emptyList()
        }

        val allPersons = mutableSetOf<EntityRef>()
        val allGroups = mutableSetOf<EntityRef>()
        authorities.forEach { authority ->
            if (authority.getSourceId() == AuthorityType.GROUP.sourceId) {
                allGroups.add(authority)
            } else if (isValidUser(authority)) {
                allPersons.add(authority)
            }
        }

        if (allGroups.isNotEmpty()) {
            allPersons.addAll(getPersonsFromGroups(allGroups))
        }
        if (allPersons.isEmpty()) {
            return emptyList()
        }

        return recordsService.getAtts(allPersons, listOf("email")).map {
            it.getAtt("email").asText()
        }.filter {
            it.isNotBlank()
        }
    }

    private fun isValidUser(personRef: EntityRef): Boolean {
        return !recordsService.getAtt(personRef, ATT_IS_EXTERNAL_USER).asBoolean()
    }

    private fun getPersonsFromGroups(groups: Set<EntityRef>): List<EntityRef> {
        return recordsService.query(
            RecordsQuery.create {
                withSourceId(AuthorityType.PERSON.sourceId)
                withQuery(
                    Predicates.and(
                        Predicates.`in`(AuthorityConstants.ATT_AUTHORITY_GROUPS, groups),
                        Predicates.eq(ATT_IS_EXTERNAL_USER, false)
                    )
                )
            }
        ).getRecords()
    }

    private fun WorkspaceMember.isUserJoin(): Boolean {
        return memberId?.startsWith(USER_JOIN_PREFIX) ?: false
    }

    private fun WorkspaceMember.isCreator(): Boolean {
        return memberId == CREATOR_MEMBER_ID
    }

    private data class WorkspaceCreated(
        val record: EntityRef,
        @AttName("record.workspaceMembers[]?json")
        val members: List<WorkspaceMember>
    )

    private data class WorkspaceChanged(
        val record: EntityRef,
        @AttName("diff.list[]?json")
        val changed: List<DiffData>,
    )

    private data class WorkspaceMember(
        val memberId: String?,
        val authorities: Set<EntityRef>
    )

    private data class WorkspaceMemberChanged(
        @AttName("diff.list[]?json")
        val changed: List<DiffData>,
        @AttName("record._parent?id")
        val workspaceRef: EntityRef
    )

    private data class WorkspaceMemberDeleted(
        @AttName("record?json")
        var member: WorkspaceMember,
        @AttName("record._parent?id")
        val workspaceRef: EntityRef
    )

    private data class DiffData(
        val id: String,
        val added: Set<EntityRef> = emptySet(),
        val removed: Set<EntityRef> = emptySet()
    )
}
