package ru.citeck.ecos.model.domain.comments.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.springframework.stereotype.Component
import ru.citeck.ecos.data.sql.records.listener.DbRecordChangedEvent
import ru.citeck.ecos.data.sql.records.listener.DbRecordCreatedEvent
import ru.citeck.ecos.data.sql.records.listener.DbRecordsListenerAdapter
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.emitter.EmitterConfig
import ru.citeck.ecos.events2.emitter.EventsEmitter
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.notifications.lib.Notification
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.lib.service.NotificationService
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.entity.toEntityRef

@Component
class CommentsMentionListener(
    private val recordsService: RecordsService,
    private val notificationService: NotificationService,
    eventsService: EventsService
) : DbRecordsListenerAdapter() {

    companion object {
        private val log = KotlinLogging.logger {}
        private val notificationTemplate = EntityRef.create(
            AppName.NOTIFICATIONS,
            "template",
            "user-is-mentioned-in-comment"
        )
    }

    private final val emitter: EventsEmitter<UserIsMentionedInCommentEvent>

    init {
        emitter = eventsService.getEmitter(
            EmitterConfig.create<UserIsMentionedInCommentEvent>()
                .withSource(AppName.EMODEL)
                .withEventClass(UserIsMentionedInCommentEvent::class.java)
                .withEventType(UserIsMentionedInCommentEvent.TYPE)
                .build()
        )
    }

    override fun onChanged(event: DbRecordChangedEvent) {
        val textBefore = event.before[CommentDesc.ATT_TEXT] as? String ?: ""
        val textAfter = event.after[CommentDesc.ATT_TEXT] as? String ?: ""
        process(textBefore, textAfter, event.record, event.globalRef)
    }

    override fun onCreated(event: DbRecordCreatedEvent) {
        val text = recordsService.getAtt(event.record, CommentDesc.ATT_TEXT).asText()
        process("", text, event.record, event.globalRef)
    }

    private fun process(
        textBefore: String,
        textAfter: String,
        record: Any,
        commentRef: EntityRef
    ) {

        val mentionsBefore = extractMentions(textBefore)
        val mentionsAfter = extractMentions(textAfter)

        val newMentions = LinkedHashSet(mentionsAfter)
        mentionsBefore.forEach { newMentions.remove(it) }

        if (newMentions.isEmpty()) {
            return
        }

        val recordRef = recordsService.getAtt(record, "record?id").asText().toEntityRef()
        val recWorkspaceId = recordsService.getAtt(
            recordRef,
            RecordConstants.ATT_WORKSPACE + ScalarType.LOCAL_ID_SCHEMA
        ).asText()
        var recWorkspaceRef = EntityRef.EMPTY
        if (recWorkspaceId.isNotBlank()) {
            recWorkspaceRef = WorkspaceDesc.getRef(recWorkspaceId)
        }

        for (mention in newMentions) {
            val userRef = if (mention.startsWith(AppName.EMODEL + EntityRef.APP_NAME_DELIMITER)) {
                EntityRef.valueOf(mention)
            } else {
                AuthorityType.PERSON.getRef(mention)
            }
            log.debug {
                "User is mentioned in comment. RecordRef: $recordRef CommentRef: $commentRef Text: $textAfter"
            }
            val userAtts = recordsService.getAtts(userRef, UserAtts::class.java)
            val event = UserIsMentionedInCommentEvent(
                recordRef,
                commentRef,
                userRef,
                textAfter,
                userAtts.externalUser,
                recWorkspaceRef
            )
            if (!userAtts.externalUser && userAtts.email.isNotBlank() && userAtts.email.contains("@")) {
                notificationService.send(
                    Notification.Builder()
                        .addRecipient(userAtts.email)
                        .record(event)
                        .notificationType(NotificationType.EMAIL_NOTIFICATION)
                        .templateRef(notificationTemplate)
                        .build()
                )
            }
            emitter.emit(event)
        }
    }

    private fun extractMentions(text: String): Set<String> {
        if (text.isBlank()) {
            return emptySet()
        }
        val doc = Jsoup.parse(text, "", Parser.xmlParser())

        val mentions = LinkedHashSet<String>()
        for (span in doc.getElementsByTag("span")) {
            val mention = span.attributes().get("data-mention")
            if (mention.isNotBlank()) {
                mentions.add(mention)
            }
        }
        return mentions
    }

    class UserAtts(
        @AttName("authorities._has.GROUP_EXTERNAL_USERS?bool!")
        val externalUser: Boolean,
        @AttName("email!")
        val email: String
    )

    class UserIsMentionedInCommentEvent(
        val record: EntityRef,
        val commentRecord: EntityRef,
        val user: EntityRef,
        val text: String? = null,
        val externalUser: Boolean,
        @AttName(RecordConstants.ATT_WORKSPACE)
        val workspace: EntityRef
    ) {
        companion object {
            const val TYPE = "user-is-mentioned-in-comment"
        }
    }
}
