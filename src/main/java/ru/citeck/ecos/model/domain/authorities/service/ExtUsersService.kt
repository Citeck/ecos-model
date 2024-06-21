package ru.citeck.ecos.model.domain.authorities.service

import org.springframework.stereotype.Service
import ru.citeck.ecos.commons.task.schedule.Schedules
import ru.citeck.ecos.events2.EventsService
import ru.citeck.ecos.events2.type.RecordChangedEvent
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityGroupConstants
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.task.EcosTasksApi
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import javax.annotation.PostConstruct

@Service
class ExtUsersService(
    private val authorityService: AuthorityService,
    private val recordsService: RecordsService,
    private val eventsService: EventsService,
    private val tasksApi: EcosTasksApi
) {
    companion object {
        private const val EXT_USERS_GROUP_AUTH_NAME = "GROUP_" + AuthorityGroupConstants.EXTERNAL_USERS_GROUP
    }

    private var extPortalsUrl = AtomicReference<List<Pair<String,String>>>()

    @PostConstruct
    fun init() {
        eventsService.addListener<Unit> {
            withEventType(RecordChangedEvent.TYPE)
            withDataClass(Unit::class.java)
            withFilter(Predicates.and(
                Predicates.eq("typeDef.id", "authority-group"),
                Predicates.eq("diff._has.${AuthorityGroupConstants.ATT_EXT_PORTAL_URL}?bool", true)
            ))
            withExclusive(false)
            withAction { extPortalsUrl.set(null) }
        }
        tasksApi.getMainScheduler().schedule(
            "reset-ext-portals-url-cache",
            Schedules.fixedDelay(Duration.ofMinutes(10))
        ) {
            extPortalsUrl.set(null)
        }
    }

    fun getExtPortalUrlForUser(userId: String): String? {
        val authorities = authorityService.getAuthoritiesForPerson(userId)

        if (!authorities.contains(EXT_USERS_GROUP_AUTH_NAME)) {
            return null
        }
        getExtPortalsUrls().forEach {
            if (authorities.contains(it.first)) {
                return it.second
            }
        }
        return null
    }

    @Synchronized
    private fun getExtPortalsUrls(): List<Pair<String, String>> {
        val currentHosts = extPortalsUrl.get()
        if (currentHosts != null) {
            return currentHosts
        }
        val calculatedPortalsUrl = calculateExtPortalsUrl()
        extPortalsUrl.set(calculatedPortalsUrl)
        return calculatedPortalsUrl
    }

    private fun calculateExtPortalsUrl(): List<Pair<String, String>> {

        val result = ArrayList<Pair<String, String>>()

        fun getExtUrl(ref: EntityRef): String {
            return recordsService.getAtt(ref, AuthorityGroupConstants.ATT_EXT_PORTAL_URL).asText()
        }

        val processedGroups = HashSet<EntityRef>()
        var currentLevelGroups: List<EntityRef> = listOf(
            AuthorityType.GROUP.getRef(AuthorityGroupConstants.EXTERNAL_USERS_GROUP)
        )
        val nextLevelGroups = LinkedHashSet<EntityRef>()
        while (currentLevelGroups.isNotEmpty()) {
            for (groupRef in currentLevelGroups) {
                if (!processedGroups.add(groupRef)) {
                    continue
                }
                val extUrl = getExtUrl(groupRef)
                if (extUrl.isNotBlank()) {
                    result.add(AuthorityType.GROUP.authorityPrefix + groupRef.getLocalId() to extUrl)
                }
                nextLevelGroups.addAll(authorityService.getGroupMembers(groupRef, AuthorityType.GROUP))
            }
            currentLevelGroups = ArrayList(nextLevelGroups)
            nextLevelGroups.clear()
        }
        return result.reversed()
    }
}
