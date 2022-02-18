package ru.citeck.ecos.model.domain.authorities.job

import mu.KotlinLogging
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component
import ru.citeck.ecos.config.lib.consumer.bean.EcosConfig
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.model.domain.authorities.constant.PersonConstants
import ru.citeck.ecos.model.domain.authsync.service.AuthorityType
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.request.RequestContext
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.concurrent.ScheduledFuture
import javax.annotation.PostConstruct

@Component
class InactiveUsersDisablingJob(
    private val recordsService: RecordsService,
    private val taskScheduler: TaskScheduler
) {

    companion object {
        private val log = KotlinLogging.logger {}

        private const val QUERY_MAX_ITEMS = 10
    }

    private var job: ScheduledFuture<*>? = null

    private var initialized = false
    private var duration: Duration? = null

    @Synchronized
    @PostConstruct
    fun init() {
        initialized = true
        updateScheduling()
    }

    private fun updateScheduling() {
        if (!initialized)  {
            return
        }
        val duration = this.duration
        if (duration == null || duration.isZero) {
            log.info { "Duration is not set. Job will be disabled." }
            job?.cancel(true)
            job = null
        } else if (job == null) {
            log.info { "Schedule job with duration $duration" }
            job = taskScheduler.scheduleWithFixedDelay(
                {
                    AuthContext.runAsSystem {
                        for (i in 1..10) {
                            val hasMore = RequestContext.doWithTxn {
                                updateUsers()
                            }
                            if (!hasMore) {
                                break
                            }
                        }
                    }
                },
                Instant.now().plus(1, ChronoUnit.MINUTES),
                Duration.ofSeconds(10)
            )
        }
    }

    private fun updateUsers(): Boolean {

        val duration = this.duration ?: return false

        val lastActivityTime = Instant.now().minus(duration)
        //todo: predicate doesn't work...
        val predicate = Predicates.and(
            Predicates.not(Predicates.eq("id", "admin")),
            Predicates.or(
                Predicates.empty(PersonConstants.ATT_PERSON_DISABLED),
                Predicates.eq(PersonConstants.ATT_PERSON_DISABLED, "false")
            ),
            Predicates.notEmpty(PersonConstants.ATT_LAST_ENABLED_TIME),
            Predicates.or(
                Predicates.and(
                    Predicates.empty(PersonConstants.ATT_LAST_LOGIN_TIME),
                    Predicates.le(PersonConstants.ATT_LAST_ENABLED_TIME, lastActivityTime),
                ),
                Predicates.and(
                    Predicates.notEmpty(PersonConstants.ATT_LAST_LOGIN_TIME),
                    Predicates.le(PersonConstants.ATT_LAST_LOGIN_TIME, lastActivityTime),
                    Predicates.le(PersonConstants.ATT_LAST_ENABLED_TIME, lastActivityTime),
                )
            )
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
            recordsService.mutateAtt(person, PersonConstants.ATT_PERSON_DISABLED, true)
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
