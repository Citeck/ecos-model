package ru.citeck.ecos.model.domain.authorities.service

import com.hazelcast.core.HazelcastInstance
import com.hazelcast.core.IMap
import org.springframework.stereotype.Service
import ru.citeck.ecos.model.domain.authorities.AuthorityConstants
import ru.citeck.ecos.model.domain.authsync.service.AuthorityType
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.request.RequestContext
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@Service
class AuthorityService(
    hazelcast: HazelcastInstance,
    val records: RecordsService
) {

    companion object {
        private const val EXPANDED_GROUPS_CACHE_KEY = "expanded-authority-groups-cache"
        private const val CTX_UPDATE_GROUPS_CACHE_KEY = "ctx-update-groups-cache-key"

        private const val ATT_AUTHORITY_GROUPS = "authorityGroups[]?localId"
    }

    private val expandedGroupsCache: IMap<String, Set<String>>

    init {
        expandedGroupsCache = hazelcast.getMap(EXPANDED_GROUPS_CACHE_KEY)
    }

    fun getAuthoritiesForPerson(personId: String): Set<String> {

        val personRef = RecordRef.create("person", personId)
        val personGroups = records.getAtt(personRef, ATT_AUTHORITY_GROUPS).asStrList()

        val authorities = LinkedHashSet<String>()
        authorities.add(personId)
        for (group in personGroups) {
            val expGroups = getExpandedGroups(group)
            for (expGroup in expGroups) {
                authorities.add("GROUP_$expGroup")
            }
        }
        authorities.add("GROUP_EVERYONE")

        return authorities
    }

    private fun getExpandedGroups(groupId: String): Set<String> {

        val cachedAuthorities = expandedGroupsCache[groupId]
        if (cachedAuthorities != null) {
            return cachedAuthorities
        }

        val result = LinkedHashSet<String>()
        result.add(groupId)

        val groupRef = RecordRef.create(AuthorityType.GROUP.sourceId, groupId)
        val groupGroups = records.getAtt(groupRef, ATT_AUTHORITY_GROUPS).asStrList()

        for (group in groupGroups) {
            result.addAll(getExpandedGroups(group))
        }

        // random shift to avoid cache massive eviction
        val ttl = + TimeUnit.MINUTES.toSeconds(30) + (Random.nextFloat() * 30).toLong()

        expandedGroupsCache.put(groupId, result, ttl, TimeUnit.SECONDS)
        return result
    }

    fun resetCache(groupId: String) {

        if (groupId.isEmpty()) {
            return
        }

        val ctx = RequestContext.getCurrentNotNull()
        val groupsToResetCache = ctx.getSet<String>(CTX_UPDATE_GROUPS_CACHE_KEY)
        groupsToResetCache.add(groupId)

        if (groupsToResetCache.size == 1) {
            ctx.doAfterCommit {
                val processedGroups = HashSet<String>()
                groupsToResetCache.forEach {
                    forEachDescendentGroup(it, processedGroups) { ref ->
                        expandedGroupsCache.remove(ref.id)
                    }
                }
                groupsToResetCache.clear()
            }
        }
    }

    fun getGroupMembers(groupRef: RecordRef, membersType: AuthorityType): List<RecordRef> {
        return records.query(RecordsQuery.create {
            withSourceId(membersType.sourceId)
            withQuery(Predicates.contains(AuthorityConstants.ATT_AUTHORITY_GROUPS, groupRef.toString()))
        }).getRecords()
    }

    private fun forEachDescendentGroup(groupId: String,
                                       processedGroups: MutableSet<String>,
                                       action: (RecordRef) -> Unit) {

        if (!processedGroups.add(groupId)) {
            return
        }
        val groupRef = RecordRef.create(AuthorityType.GROUP.sourceId, groupId)
        action.invoke(groupRef)

        val children = getGroupMembers(groupRef, AuthorityType.GROUP)

        for (child in children) {
            forEachDescendentGroup(child.id, processedGroups, action)
        }
    }
}
