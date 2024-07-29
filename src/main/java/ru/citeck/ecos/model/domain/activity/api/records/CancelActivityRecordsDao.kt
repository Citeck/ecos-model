package ru.citeck.ecos.model.domain.activity.api.records

import mu.KotlinLogging
import net.fortuna.ical4j.model.property.Method
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.utils.DurationStrUtils
import ru.citeck.ecos.model.domain.activity.config.ActivityStatus
import ru.citeck.ecos.model.lib.status.constants.StatusConstants
import ru.citeck.ecos.notifications.lib.Notification
import ru.citeck.ecos.notifications.lib.NotificationType
import ru.citeck.ecos.notifications.lib.service.NotificationService
import ru.citeck.ecos.notifications.lib.utils.CalendarEventUtils
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
        log.info { "Start cancellation activity ${value.recordRef}" }
        deleteActiveProcess(value.recordRef)
        sendNotification(value)
        updateStatus(value.recordRef)
        log.info { "Canceled activity ${value.recordRef}" }
        return ""
    }

    private fun deleteActiveProcess(activity: EntityRef) {
        val processList = recordsService.query(
            RecordsQuery.create()
                .withSourceId("eproc/bpmn-proc")
                .withLanguage(PredicateService.LANGUAGE_PREDICATE)
                .withQuery(Predicates.eq("document", activity))
                .withPage(
                    QueryPage.create()
                        .withMaxItems(10)
                        .build())
                .build()
        ).getRecords()

        for (process in processList) {
            recordsService.mutate(
                process,
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
        val canceledCalendarEventAttach = CalendarEventUtils.createCalendarEventAttachment(
            activity.calendarEventSummary,
            activity.description,
            activity.activityDate,
            DurationStrUtils.parseDurationToMillis(activity.duration),
            activity.organizerEmail,
            listOf(activity.responsibleEmail),
            uid = activity.calendarEventUid,
            sequence = sequence,
            method = Method.CANCEL
        )
        val additionalMeta = mapOf(
            NOTIFICATION_ATTACHMENTS to canceledCalendarEventAttach
        )

        notificationService.send(
            Notification.Builder()
                .addRecipient(activity.responsibleEmail)
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
        val organizerEmail: String,
        val activityDate: Instant,
        val duration: String,
        val description: String,
        val calendarEventSummary: String,
        val calendarEventUid: String,
        val calendarEventSequence: Int
    )
}
