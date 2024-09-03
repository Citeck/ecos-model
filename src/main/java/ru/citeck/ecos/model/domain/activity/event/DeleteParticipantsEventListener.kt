package ru.citeck.ecos.model.domain.activity.event

import net.fortuna.ical4j.model.property.Method
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.utils.DurationStrUtils
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.type.RecordChangedEvent
import ru.citeck.ecos.events2.type.RecordChangedEvent.AssocDiff
import ru.citeck.ecos.notifications.lib.Notification
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.lib.icalendar.CalendarEvent
import ru.citeck.ecos.notifications.lib.service.NotificationService
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.time.Instant

@Component
class DeleteParticipantsEventListener(
    eventsService: EventsService,
    private val recordsService: RecordsService,
    private val notificationService: NotificationService
) {

    companion object {
        private const val PARTICIPANTS_ATT = "participants"
        private const val NOTIFICATION_ATTACHMENTS = "_attachments"

        private val notificationTemplate = EntityRef.create(
            AppName.NOTIFICATIONS,
            "template",
            "activity-calendar-event-cancel-notification"
        )
    }

    init {
        eventsService.addListener<EventData> {
            withTransactional(true)
            withEventType(RecordChangedEvent.TYPE)
            withDataClass(EventData::class.java)
            withFilter(
                Predicates.and(
                    Predicates.eq("record._type.isSubTypeOf.planned-activity?bool", true),
                    Predicates.eq("diff._has.participants?bool", true)
                )
            )
            withAction { sendNotification(it) }
        }
    }

    private fun sendNotification(event: EventData) {
        val removedParticipantsRefs = event.assocs
            .first { it.assocId == PARTICIPANTS_ATT }
            .removed

        val recipients = recordsService.getAtts(removedParticipantsRefs, listOf("email"))
            .map { it.getAtt("email").asText() }
            .toList()

        val sequence = event.calendarEventSequence + 1
        val durationInMillis = DurationStrUtils.parseDurationToMillis(event.activityDuration)
        val canceledCalendarEvent = CalendarEvent.Builder(event.calendarEventSummary, event.activityDate)
            .uid(event.calendarEventUid)
            .sequence(sequence)
            .durationInMillis(durationInMillis)
            .organizer(event.activityOrganizer)
            .attendees(recipients)
            .method(Method.CANCEL)
            .build()
        val canceledCalendarEventAttach = canceledCalendarEvent.createAttachment()
        val additionalMeta = mapOf(
            NOTIFICATION_ATTACHMENTS to canceledCalendarEventAttach
        )

        notificationService.send(
            Notification.Builder()
                .recipients(recipients)
                .record(event.record)
                .notificationType(NotificationType.EMAIL_NOTIFICATION)
                .templateRef(notificationTemplate)
                .additionalMeta(additionalMeta)
                .build()
        )
    }

    private data class EventData(
        val record: EntityRef,
        val assocs: List<AssocDiff>,
        @AttName("record.activityDate")
        val activityDate: Instant,
        @AttName("record.activityDuration")
        val activityDuration: String,
        @AttName("record._creator.email")
        val activityOrganizer: String,
        @AttName("record.calendarEventSummary")
        val calendarEventSummary: String,
        @AttName("record.calendarEventUid")
        val calendarEventUid: String,
        @AttName("record.calendarEventSequence")
        val calendarEventSequence: Int
    )
}
