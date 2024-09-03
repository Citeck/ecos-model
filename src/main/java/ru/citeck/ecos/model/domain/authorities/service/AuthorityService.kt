package ru.citeck.ecos.model.domain.authorities.service

import com.hazelcast.core.HazelcastInstance
import com.hazelcast.crdt.pncounter.PNCounter
import com.hazelcast.map.IMap
import org.springframework.stereotype.Service
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthGroup
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityConstants
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityGroupConstants
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.iter.IterableRecords
import ru.citeck.ecos.records3.iter.IterableRecordsConfig
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.request.RequestContext
import ru.citeck.ecos.txn.lib.TxnContext
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.util.LinkedList
import java.util.concurrent.locks.ReentrantLock

@Service
class AuthorityService(
    hazelcast: HazelcastInstance,
    private val records: RecordsService
) {

    companion object {
        private const val PERSON_AUTHORITIES_CACHE_KEY = "person-authorities-cache"

        private const val AUTH_GROUPS_TREE_VERSION_COUNTER_KEY = "auth-groups-tree-version-counter"

        private const val CTX_UPDATE_GROUPS_CACHE_KEY = "ctx-update-groups-cache-key"

        private const val ATT_AUTHORITY_GROUPS = "authorityGroups[]?localId"

        val ADMIN_GROUPS = setOf(
            AuthorityGroupConstants.ADMIN_GROUP,
            "ALFRESCO_ADMINISTRATORS"
        )

        val ADMIN_GROUPS_AUTH_NAME = ADMIN_GROUPS.mapTo(LinkedHashSet()) { AuthGroup.PREFIX + it }
    }

    private final val personAuthoritiesCache: IMap<String, Set<String>> = hazelcast.getMap(PERSON_AUTHORITIES_CACHE_KEY)

    @Volatile
    private var authGroupsTree = AuthGroupsTree(-1)
    private val authGroupsTreeBuildLock = ReentrantLock()
    private final val authGroupsVersionCounter: PNCounter = hazelcast.getPNCounter(AUTH_GROUPS_TREE_VERSION_COUNTER_KEY)

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

    fun integrityCheckBeforeAddToParents(child: String, parents: Set<String>) {
        if (parents.contains(child)) {
            error("Group can't be added to itself. Id: $child")
        }
        val checkedGroups = HashSet<String>()
        for (rootParent in parents) {
            val parentsToCheck = LinkedList<String>()
            parentsToCheck.add(rootParent)
            while (parentsToCheck.isNotEmpty()) {
                val parentId = parentsToCheck.poll()
                if (!checkedGroups.add(parentId)) {
                    continue
                }
                val parentsOfParent = records.getAtt(
                    AuthorityType.GROUP.getRef(parentId),
                    ATT_AUTHORITY_GROUPS
                ).asStrList()
                if (parentsOfParent.contains(child)) {
                    error("Cyclic dependency. Group '$child' can't be added to group: $rootParent")
                }
                parentsToCheck.addAll(parentsOfParent)
            }
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
            forEachGroup(groupIdWithoutPrefix, processedGroups, asc) { result.add(it) }
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
            TxnContext.doAfterCommit(-1000f, false) {
                personAuthoritiesCache.clear()
                authGroupsVersionCounter.incrementAndGet()
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

    private fun getAuthGroupsTree(): AuthGroupsTree {
        val version = authGroupsVersionCounter.get()
        if (authGroupsTree.version != version) {
            authGroupsTreeBuildLock.lock()
            try {
                if (authGroupsTree.version != version) {
                    AuthContext.runAsSystem {
                        authGroupsTree = buildAuthGroupsTree(version)
                    }
                }
            } finally {
                authGroupsTreeBuildLock.unlock()
            }
        }
        return authGroupsTree
    }

    private fun buildAuthGroupsTree(version: Long): AuthGroupsTree {

        val newTree = AuthGroupsTree(version)

        val iterableRecords = IterableRecords(
            RecordsQuery.create()
                .withSourceId(AuthorityType.GROUP.sourceId)
                .withQuery(Predicates.alwaysTrue())
                .build(),
            IterableRecordsConfig.create()
                .withAttsToLoad(mapOf("groups" to ATT_AUTHORITY_GROUPS))
                .build(),
            records
        )
        val recordsIt = iterableRecords.iterator()
        while (recordsIt.hasNext()) {
            val record = recordsIt.next()
            val group = newTree.getOrCreateGroup(record.getId().getLocalId())
            for (groupIdData in record.getAtt("groups")) {
                val groupId = groupIdData.asText()
                if (groupId.isNotBlank()) {
                    group.addParent(newTree.getOrCreateGroup(groupId))
                }
            }
        }
        return newTree
    }

    private fun forEachGroup(
        groupId: String,
        processedGroups: MutableSet<String>,
        asc: Boolean,
        action: (String) -> Unit
    ) {
        if (!processedGroups.add(groupId)) {
            return
        }

        action.invoke(groupId)

        val authGroupsTree = getAuthGroupsTree()
        val group = authGroupsTree.getGroup(groupId) ?: return
        if (asc) {
            group.doWithEachParentFull { action.invoke(it.id) }
        } else {
            group.doWithEachChildFull { action.invoke(it.id) }
        }
    }
}
