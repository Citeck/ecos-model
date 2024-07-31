package ru.citeck.ecos.model.domain.activity.job

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.task.schedule.Schedules
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.emitter.EmitterConfig
import ru.citeck.ecos.events2.emitter.EventsEmitter
import ru.citeck.ecos.model.domain.activity.config.ActivityConfiguration
import ru.citeck.ecos.model.domain.activity.config.ActivityStatus
import ru.citeck.ecos.model.lib.status.constants.StatusConstants
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.task.scheduler.EcosTaskSchedulerApi
import ru.citeck.ecos.webapp.lib.lock.EcosAppLockService
import java.time.Duration
import java.time.Instant
import javax.annotation.PostConstruct

@Component
class ActivityStartUserTaskJob(
    private val recordsService: RecordsService,
    private val eventsService: EventsService,
    private val taskScheduler: EcosTaskSchedulerApi,
    private val appLockService: EcosAppLockService
) {

    companion object {
        private val log = KotlinLogging.logger {}

        private const val MAX_ITERATION = 10_000
        private const val ATT_ACTIVITY_DATE = "activityDate"
    }

    @Value("\${ecos.job.activityStartUserTask.cron}")
    private lateinit var cron: String

    private lateinit var activityStartProcessEmitter: EventsEmitter<ActivityStartProcessEvent>

    @PostConstruct
    fun init() {
        activityStartProcessEmitter = eventsService.getEmitter(
            EmitterConfig.create {
                withEventType(ActivityStartProcessEvent.TYPE)
                withSource(ActivityStartProcessEvent::class.simpleName)
                withEventClass(ActivityStartProcessEvent::class.java)
            }
        )

        taskScheduler.schedule(
            "ActivityStartUserTaskJob", Schedules.cron(cron)
        ) {
            appLockService.doInSyncOrSkip(
                "ActivityStartUserTaskJob", Duration.ofSeconds(10)
            ) { sync() }
        }
    }

    private fun sync() {
        var iter = 0
        var activities = getActivities()
        while (activities.isNotEmpty() && iter < MAX_ITERATION) {
            for (activity in activities) {
                updateStatus(activity)

                val event = ActivityStartProcessEvent(activity)
                activityStartProcessEmitter.emit(event)
                log.debug { "Start user task for ${activity.getLocalId()}" }
            }
            activities = getActivities()
            iter++
        }
    }

    private fun getActivities(): List<EntityRef> {
        val query = RecordsQuery.create {
            withSourceId("${AppName.EMODEL}/${ActivityConfiguration.ACTIVITY_DAO_ID}")
            withLanguage(PredicateService.LANGUAGE_PREDICATE)
            withQuery(
                Predicates.and(
                    Predicates.le(ATT_ACTIVITY_DATE, Instant.now()),
                    Predicates.eq(StatusConstants.ATT_STATUS, ActivityStatus.PLANNED.id)
                )
            )
            addSort(SortBy(RecordConstants.ATT_CREATED, true))
            withMaxItems(100)
        }
        return recordsService.query(query).getRecords()
    }

    private fun updateStatus(activity: EntityRef) {
        recordsService.mutate(
            activity,
            mapOf(
                StatusConstants.ATT_STATUS to ActivityStatus.EXPIRED.id
            )
        )
    }

    class ActivityStartProcessEvent(
        val record: EntityRef
    ) {
        companion object {
            const val TYPE = "activity-start-user-task"
        }
    }
}
