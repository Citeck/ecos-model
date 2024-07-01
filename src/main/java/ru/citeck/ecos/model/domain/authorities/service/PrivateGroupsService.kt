package ru.citeck.ecos.model.domain.authorities.service

import org.springframework.stereotype.Service
import ru.citeck.ecos.commons.task.schedule.Schedules
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthGroup
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.type.RecordChangedEvent
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityGroupConstants
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.task.EcosTasksApi
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import javax.annotation.PostConstruct

@Service
class PrivateGroupsService(
    private val eventsService: EventsService,
    private val recordsService: RecordsService,
    private val tasksApi: EcosTasksApi
) {

    private val privateGroups = AtomicReference<Set<String>>()

    @PostConstruct
    fun init() {
        eventsService.addListener<Unit> {
            withEventType(RecordChangedEvent.TYPE)
            withDataClass(Unit::class.java)
            withFilter(
                Predicates.and(
                    Predicates.eq("typeDef.id", "authority-group"),
                    Predicates.eq("diff._has.${AuthorityGroupConstants.ATT_PRIVATE_GROUP}?bool", true)
                )
            )
            withExclusive(false)
            withAction { privateGroups.set(null) }
        }
        tasksApi.getMainScheduler().schedule(
            "reset-private-groups-cache",
            Schedules.fixedDelay(Duration.ofMinutes(1))
        ) {
            privateGroups.set(null)
        }
    }

    @Synchronized
    fun getPrivateGroups(): Set<String> {
        val groups = privateGroups.get()
        if (groups != null) {
            return groups
        }
        val queryRes = AuthContext.runAsSystem {
            recordsService.query(
                RecordsQuery.create()
                    .withEcosType(AuthorityGroupConstants.TYPE_ID)
                    .withQuery(Predicates.eq(AuthorityGroupConstants.ATT_PRIVATE_GROUP, true))
                    .build()
            )
        }
        val newGroups = queryRes.getRecords().mapTo(LinkedHashSet()) {
            AuthGroup.PREFIX + it.getLocalId()
        }
        this.privateGroups.set(newGroups)
        return newGroups
    }
}
