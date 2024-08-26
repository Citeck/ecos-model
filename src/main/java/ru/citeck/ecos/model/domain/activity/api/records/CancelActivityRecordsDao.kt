package ru.citeck.ecos.model.domain.activity.api.records

import io.github.oshai.kotlinlogging.KotlinLogging
import net.fortuna.ical4j.model.property.Method
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.utils.DurationStrUtils
import ru.citeck.ecos.model.domain.activity.config.ActivityConfiguration
import ru.citeck.ecos.model.domain.activity.config.ActivityStatus
import ru.citeck.ecos.model.lib.status.constants.StatusConstants
import ru.citeck.ecos.notifications.lib.Notification
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.lib.icalendar.CalendarEvent
import ru.citeck.ecos.notifications.lib.service.NotificationService
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.mutate.ValueMutateDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.QueryPage
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.time.Instant

@Component
class CancelActivityRecordsDao(
    private val notificationService: NotificationService
) : AbstractRecordsDao(), ValueMutateDao<CancelActivityRecordsDao.ActivityDto> {

    companion object {
        const val ID = "cancel-activity"
        private val SOURCE_ID =
            "${AppName.EMODEL}${EntityRef.APP_NAME_DELIMITER}${ActivityConfiguration.ACTIVITY_DAO_ID}"
        private const val PROCESS_DEFINITION_KEY = "ecos-activity-process"
        private const val NOTIFICATION_ATTACHMENTS = "_attachments"

        private val log = KotlinLogging.logger {}
        private val notificationTemplate = EntityRef.create(
            AppName.NOTIFICATIONS,
            "template",
            "activity-calendar-event-cancel-notification"
        )
    }

    override fun getId(): String {
        return ID
    }

    override fun mutate(value: ActivityDto): Any? {
        val record = value.recordRef
        val sourceId = "${record.getAppName()}${EntityRef.APP_NAME_DELIMITER}${record.getSourceId()}"
        if (SOURCE_ID != sourceId) {
            throw IllegalArgumentException("Action not supported for $sourceId")
        }

        log.info { "Start cancellation activity $record" }
        deleteActiveProcess(record)
        sendNotification(value)
        updateStatus(record)
        log.info { "Canceled activity $record" }
        return ""
    }

    private fun deleteActiveProcess(activity: EntityRef) {
        val process = recordsService.queryOne(
            RecordsQuery.create()
                .withSourceId("eproc/bpmn-proc")
                .withLanguage(PredicateService.LANGUAGE_PREDICATE)
                .withQuery(
                    Predicates.and(
                        Predicates.eq("processDefinitionKey", PROCESS_DEFINITION_KEY),
                        Predicates.eq("document", activity)
                    )
                )
                .withPage(
                    QueryPage.create()
                        .withMaxItems(1)
                        .build()
                ).build()
        )

        process?.let {
            recordsService.mutate(
                it,
                mapOf(
                    "action" to "DELETE",
                    "skipCustomListener" to false,
                    "skipIoMapping" to false
                )
            )
            log.info { "Successful delete process $process" }
        }
    }

    private fun sendNotification(activity: ActivityDto) {
        val sequence = activity.calendarEventSequence + 1
        val durationInMillis = DurationStrUtils.parseDurationToMillis(activity.duration)
        val recipients = mutableSetOf(activity.responsibleEmail)
        recipients.addAll(activity.participantsEmails)
        val canceledCalendarEvent = CalendarEvent.Builder(activity.calendarEventSummary, activity.activityDate)
            .uid(activity.calendarEventUid)
            .sequence(sequence)
            .description(activity.description)
            .durationInMillis(durationInMillis)
            .organizer(activity.organizerEmail)
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
                .record(activity.recordRef)
                .notificationType(NotificationType.EMAIL_NOTIFICATION)
                .templateRef(notificationTemplate)
                .additionalMeta(additionalMeta)
                .build()
        )
    }

    private fun updateStatus(activity: EntityRef) {
        recordsService.mutate(
            activity,
            mapOf(
                StatusConstants.ATT_STATUS to ActivityStatus.CANCELED.id
            )
        )
    }

    data class ActivityDto(
        val recordRef: EntityRef = EntityRef.EMPTY,
        val responsibleEmail: String,
        val participantsEmails: List<String> = emptyList(),
        val organizerEmail: String,
        val activityDate: Instant,
        val duration: String,
        val description: String,
        val calendarEventSummary: String,
        val calendarEventUid: String,
        val calendarEventSequence: Int
    )
}
