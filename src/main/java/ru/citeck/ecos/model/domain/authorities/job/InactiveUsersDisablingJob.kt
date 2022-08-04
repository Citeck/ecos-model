package ru.citeck.ecos.model.domain.authorities.job

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import ru.citeck.ecos.config.lib.consumer.bean.EcosConfig
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.model.domain.authorities.constant.PersonConstants
import ru.citeck.ecos.model.domain.authsync.service.AuthorityType
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.request.RequestContext
import ru.citeck.ecos.webapp.api.task.scheduler.EcosScheduledTask
import ru.citeck.ecos.webapp.api.task.scheduler.EcosTaskScheduler
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.annotation.PostConstruct

@Component
class InactiveUsersDisablingJob(
    private val recordsService: RecordsService,
    private val taskScheduler: EcosTaskScheduler
) {

    companion object {
        private val log = KotlinLogging.logger {}

        private const val QUERY_MAX_ITEMS = 10
    }

    @Value("\${ecos.job.inactiveUsersDisabling.cron}")
    private lateinit var cron: String

    private var job: EcosScheduledTask? = null

    private var initialized = false
    private var duration: Duration? = null

    @Synchronized
    @PostConstruct
    fun init() {
        initialized = true
        updateScheduling()
    }

    private fun updateScheduling() {
        if (!initialized) {
            return
        }
        val duration = this.duration
        if (duration == null || duration.isZero || cron.isBlank()) {
            val reason = if (cron.isBlank()) {
                "Cron is blank"
            } else {
                "Duration is not set"
            }
            log.info { "$reason. Job will be disabled." }
            job?.cancel()
            job = null
        } else if (job == null) {
            log.info { "Schedule job with duration $duration" }
            job = taskScheduler.scheduleByCron(
                "inactive-users-disabling",
                cron
            ) {
                log.info { "Users updating started..." }
                AuthContext.runAsSystem {
                    for (i in 1..10) {
                        if (!updateUsers()) {
                            break
                        }
                    }
                }
                log.info { "Users updating completed." }
            }
        }
    }

    private fun updateUsers(): Boolean {

        val duration = this.duration ?: return false
        val reason = "User inactive during $duration"

        val lastActivityTime = Instant.now().minus(duration)
        val predicate = Predicates.and(
            Predicates.not(Predicates.eq("id", "admin")),
            Predicates.or(
                Predicates.empty(PersonConstants.ATT_PERSON_DISABLED),
                Predicates.eq(PersonConstants.ATT_PERSON_DISABLED, false)
            ),
            Predicates.notEmpty(PersonConstants.ATT_LAST_ACTIVITY_TIME),
            Predicates.le(PersonConstants.ATT_LAST_ACTIVITY_TIME, lastActivityTime)
        )

        val query = RecordsQuery.create {
            withSourceId(AuthorityType.PERSON.sourceId)
            withQuery(predicate)
            withMaxItems(QUERY_MAX_ITEMS)
        }

        val queryRes = recordsService.query(query)
        val personsToDisable = queryRes.getRecords()

        if (personsToDisable.isEmpty()) {
            return false
        }

        log.info { "Found ${personsToDisable.size} persons to disable" }

        for (person in personsToDisable) {
            log.info { "Disable person '${person.id}'" }
            RequestContext.doWithTxn {
                recordsService.mutate(
                    person,
                    mapOf(
                        PersonConstants.ATT_PERSON_DISABLE_REASON to reason,
                        PersonConstants.ATT_PERSON_DISABLED to true
                    )
                )
            }
        }

        return queryRes.getRecords().size == QUERY_MAX_ITEMS
    }

    @Synchronized
    @EcosConfig("inactivity-duration-before-user-disabling")
    fun setInactivityDuration(duration: String) {
        val newDuration = try {
            Duration.parse(duration)
        } catch (e: DateTimeParseException) {
            log.error { "Incorrect duration string: '$duration'" }
            null
        }
        if (newDuration != this.duration) {
            log.info { "Duration was changed. Before: ${this.duration} After: $newDuration" }
            this.duration = newDuration
            updateScheduling()
        }
    }
}
