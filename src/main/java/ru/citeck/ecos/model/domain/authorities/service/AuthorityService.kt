package ru.citeck.ecos.model.domain.authorities.service

import com.hazelcast.core.HazelcastInstance
import com.hazelcast.core.IMap
import org.springframework.stereotype.Service
import ru.citeck.ecos.context.lib.auth.AuthGroup
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityConstants
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityGroupConstants
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.request.RequestContext
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@Service
class AuthorityService(
    hazelcast: HazelcastInstance,
    private val records: RecordsService
) {

    companion object {
        private const val ASC_GROUPS_CACHE_KEY = "asc-authority-groups-cache"
        private const val DESC_GROUPS_CACHE_KEY = "desc-authority-groups-cache"
        private const val PERSON_AUTHORITIES_CACHE_KEY = "person-authorities-cache"

        private const val CTX_UPDATE_GROUPS_CACHE_KEY = "ctx-update-groups-cache-key"

        private const val ATT_AUTHORITY_GROUPS = "authorityGroups[]?localId"

        val ADMIN_GROUPS = setOf(
            AuthorityGroupConstants.ADMIN_GROUP,
            "ALFRESCO_ADMINISTRATORS"
        )

        val ADMIN_GROUPS_AUTH_NAME = ADMIN_GROUPS.mapTo(LinkedHashSet()) { AuthGroup.PREFIX + it }
    }

    private final val ascGroupsCache: IMap<String, Set<String>>
    private final val descGroupsCache: IMap<String, Set<String>>
    private final val personAuthoritiesCache: IMap<String, Set<String>>

    init {
        ascGroupsCache = hazelcast.getMap(ASC_GROUPS_CACHE_KEY)
        descGroupsCache = hazelcast.getMap(DESC_GROUPS_CACHE_KEY)
        personAuthoritiesCache = hazelcast.getMap(PERSON_AUTHORITIES_CACHE_KEY)
    }

    fun isAdminsGroup(groupId: String): Boolean {
        val groupIdWithoutPrefix = if (groupId.startsWith(AuthGroup.PREFIX)) {
            groupId.substring(AuthGroup.PREFIX.length)
        } else {
            groupId
        }
        if (ADMIN_GROUPS.contains(groupIdWithoutPrefix)) {
            return true
        }
        val expandedGroups = getExpandedGroups(groupIdWithoutPrefix, true)
        return ADMIN_GROUPS.any { expandedGroups.contains(it) }
    }

    fun isAdmin(personId: String): Boolean {
        return getAuthoritiesForPerson(personId).contains(AuthRole.ADMIN)
    }

    fun getAuthoritiesForPerson(personId: String): Set<String> {
        return personAuthoritiesCache.computeIfAbsent(personId) {
            getAuthoritiesForPersonImpl(personId)
        }
    }

    private fun getAuthoritiesForPersonImpl(personId: String): Set<String> {

        val personRef = EntityRef.create("person", personId)
        val personGroups = records.getAtt(personRef, ATT_AUTHORITY_GROUPS).asStrList()

        val authorities = LinkedHashSet<String>()
        authorities.add(personId)
        for (group in personGroups) {
            val expGroups = getExpandedGroups(group, true)
            for (expGroup in expGroups) {
                authorities.add(AuthGroup.PREFIX + expGroup)
            }
        }
        if (ADMIN_GROUPS_AUTH_NAME.any { authorities.contains(it) }) {
            authorities.add(AuthRole.ADMIN)
        }
        authorities.add(AuthGroup.EVERYONE)
        authorities.add(AuthRole.USER)

        return authorities
    }

    fun getExpandedGroups(groupId: String, asc: Boolean): Set<String> {
        return getExpandedGroups(listOf(groupId), asc)
    }

    fun getExpandedGroups(groupsId: List<String>, asc: Boolean): Set<String> {
        val result = LinkedHashSet<String>()
        val processedGroups = HashSet<String>()
        for (groupId in groupsId) {
            val groupIdWithoutPrefix = if (groupId.startsWith(AuthGroup.PREFIX)) {
                groupId.substring(AuthGroup.PREFIX.length)
            } else {
                groupId
            }
            forEachGroup(groupIdWithoutPrefix, processedGroups, true, asc) { result.add(it) }
        }
        return result
    }

    fun resetPersonCache(personId: String) {
        personAuthoritiesCache.remove(personId)
    }

    fun resetGroupCache(groupId: String) {

        if (groupId.isEmpty()) {
            return
        }

        val ctx = RequestContext.getCurrentNotNull()
        val groupsToResetCache = ctx.getSet<String>(CTX_UPDATE_GROUPS_CACHE_KEY)
        groupsToResetCache.add(groupId)

        if (groupsToResetCache.size == 1) {
            ctx.doAfterCommit {
                val ascProcessedGroups = HashSet<String>()
                val descProcessedGroups = HashSet<String>()
                groupsToResetCache.forEach {
                    forEachGroup(it, ascProcessedGroups, withCache = false, asc = true) { groupId ->
                        descGroupsCache.remove(groupId)
                    }
                    forEachGroup(it, descProcessedGroups, withCache = false, asc = false) { groupId ->
                        ascGroupsCache.remove(groupId)
                    }
                }
                groupsToResetCache.clear()
                personAuthoritiesCache.clear()
            }
        }
    }

    fun getGroupMembers(groupRef: EntityRef, membersType: AuthorityType): List<EntityRef> {
        return records.query(
            RecordsQuery.create {
                withSourceId(membersType.sourceId)
                withQuery(Predicates.contains(AuthorityConstants.ATT_AUTHORITY_GROUPS, groupRef.toString()))
            }
        ).getRecords()
    }

    private fun forEachGroup(
        groupId: String,
        processedGroups: MutableSet<String>,
        withCache: Boolean,
        asc: Boolean,
        action: (String) -> Unit
    ) {

        if (!processedGroups.add(groupId)) {
            return
        }

        action.invoke(groupId)
        val cache: IMap<String, Set<String>>? = if (withCache) {
            if (asc) {
                ascGroupsCache
            } else {
                descGroupsCache
            }
        } else {
            null
        }

        if (cache != null) {
            val cachedGroups = cache[groupId]
            if (cachedGroups != null) {
                for (cachedGroup in cachedGroups) {
                    if (processedGroups.add(cachedGroup)) {
                        action(cachedGroup)
                    }
                }
                return
            }
        }

        val groupRef = AuthorityType.GROUP.getRef(groupId)
        val nextGroups: List<String> = if (asc) {
            records.getAtt(groupRef, ATT_AUTHORITY_GROUPS).asList(EntityRef::class.java).map { it.getLocalId() }
        } else {
            getGroupMembers(groupRef, AuthorityType.GROUP).map { it.getLocalId() }
        }
        if (cache != null) {
            val groupsToCache = LinkedHashSet<String>()
            for (nextGroup in nextGroups) {
                // previous computed groups should not affect on cache of this group.
                // because of this we should not pass processedGroup for next forEach
                forEachGroup(nextGroup, HashSet(), withCache, asc) {
                    groupsToCache.add(it)
                    if (processedGroups.add(it)) {
                        action(it)
                    }
                }
            }
            // random shift to avoid cache massive eviction
            val ttl = TimeUnit.MINUTES.toSeconds(30) + (Random.nextFloat() * 60).toLong()
            cache.put(groupId, groupsToCache, ttl, TimeUnit.SECONDS)
        } else {
            for (nextGroup in nextGroups) {
                forEachGroup(nextGroup, processedGroups, withCache, asc, action)
            }
        }
    }
}
