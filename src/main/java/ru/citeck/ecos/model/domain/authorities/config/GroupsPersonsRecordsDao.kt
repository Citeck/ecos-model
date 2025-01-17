package ru.citeck.ecos.model.domain.authorities.config

import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthGroup
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityConstants.ATT_AUTHORITY_GROUPS
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityConstants.ATT_AUTHORITY_GROUPS_FULL
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityGroupConstants
import ru.citeck.ecos.model.domain.authorities.constant.PersonConstants
import ru.citeck.ecos.model.domain.authorities.service.AuthorityService
import ru.citeck.ecos.model.domain.authorities.service.PrivateGroupsService
import ru.citeck.ecos.model.domain.authsync.service.AuthoritiesSyncService
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.PredicateUtils
import ru.citeck.ecos.records2.predicate.model.*
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.delete.DelStatus
import ru.citeck.ecos.records3.record.dao.impl.proxy.ProxyProcessor
import ru.citeck.ecos.records3.record.dao.impl.proxy.RecordsDaoProxy
import ru.citeck.ecos.records3.record.dao.mutate.RecordsMutateWithAnyResDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import ru.citeck.ecos.webapp.api.authority.EcosAuthoritiesApi
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.time.Instant
import java.time.temporal.ChronoUnit

open class GroupsPersonsRecordsDao(
    id: String,
    private val authorityType: AuthorityType,
    private val syncService: AuthoritiesSyncService,
    private val authorityService: AuthorityService,
    private val privateGroupsService: PrivateGroupsService,
    private val authoritiesApi: EcosAuthoritiesApi,
    proxyProcessor: ProxyProcessor? = null
) : RecordsDaoProxy(id, "$id-repo", proxyProcessor), RecordsMutateWithAnyResDao {

    companion object {
        private val UNIFIED_PRIVATE_GROUP_AUTH = AuthGroup.PREFIX + AuthorityGroupConstants.UNIFIED_PRIVATE_GROUP
        private val UNIFIED_PRIVATE_GROUP_REF = AuthorityType.GROUP.getRef(
            AuthorityGroupConstants.UNIFIED_PRIVATE_GROUP
        )
    }

    private val targetSourceId = "$id-repo"

    override fun delete(recordIds: List<String>): List<DelStatus> {
        if (!AuthContext.isRunAsSystemOrAdmin()) {
            error("Permission denied")
        }
        return super.delete(recordIds)
    }

    override fun getRecordsAtts(recordIds: List<String>): List<*>? {
        if (authorityType == AuthorityType.PERSON) {
            return super.getRecordsAtts(
                recordIds.map {
                    if (it == PersonConstants.CURRENT_USER_ID) {
                        AuthContext.getCurrentUser()
                    } else {
                        it
                    }
                }
            )
        }
        return super.getRecordsAtts(recordIds)
    }

    override fun queryRecords(recsQuery: RecordsQuery): RecsQueryRes<*>? {
        var newSortBy = recsQuery.sortBy
        if (authorityType == AuthorityType.PERSON && recsQuery.sortBy.isNotEmpty()) {
            newSortBy = newSortBy.map { preProcessPersonSortBy(it) }
        }
        if (recsQuery.language != PredicateService.LANGUAGE_PREDICATE) {
            return super.queryRecords(recsQuery)
        }
        var predicate = recsQuery.getQuery(Predicate::class.java)
        val authConditions = getAuthConditions()
        if (authConditions != Predicates.alwaysTrue()) {
            predicate = AndPredicate.of(predicate, authConditions)
        }
        predicate = PredicateUtils.mapValuePredicates(predicate) { pred ->

            var result: Predicate? = if (pred.getType() == ValuePredicate.Type.CONTAINS &&
                pred.getAttribute() == ATT_AUTHORITY_GROUPS_FULL
            ) {
                AuthContext.runAsSystem {
                    val values = pred.getValue().toList(EntityRef::class.java)
                    val expandedGroups = authorityService.getExpandedGroups(values.map { it.getLocalId() }, false)
                    ValuePredicate(
                        ATT_AUTHORITY_GROUPS,
                        ValuePredicate.Type.IN,
                        expandedGroups.map { AuthorityType.GROUP.getRef(it).toString() }
                    )
                }
            } else {
                pred
            }

            if (authorityType == AuthorityType.PERSON && result is ValuePredicate) {
                result = preProcessPersonValuePredicate(result)
            }

            result
        } ?: Predicates.alwaysTrue()

        return super.queryRecords(
            recsQuery.copy {
                withQuery(predicate)
                withSortBy(newSortBy)
            }
        )
    }

    private fun getAuthConditions(): Predicate {

        val currentUserAuth = AuthContext.getCurrentRunAsAuthorities()
        if (currentUserAuth.contains(AuthRole.ADMIN) ||
            currentUserAuth.contains(AuthRole.SYSTEM) ||
            currentUserAuth.contains(UNIFIED_PRIVATE_GROUP_AUTH)
        ) {
            return Predicates.alwaysTrue()
        }
        val privateGroups = privateGroupsService.getPrivateGroups()
        if (privateGroups.isEmpty()) {
            return Predicates.alwaysTrue()
        }
        val userPrivateGroups = currentUserAuth.filterTo(LinkedHashSet()) { privateGroups.contains(it) }
        val privateGroupsOutOfUser = privateGroups.subtract(userPrivateGroups).map {
            authoritiesApi.getAuthorityRef(it)
        }

        var predicate: Predicate = if (authorityType == AuthorityType.PERSON && userPrivateGroups.isNotEmpty()) {
            // users in private group can view only users from same private groups
            ValuePredicate.contains(ATT_AUTHORITY_GROUPS_FULL, userPrivateGroups)
        } else {
            // users out of private groups can view only users out of private groups
            NotPredicate(ValuePredicate.contains(ATT_AUTHORITY_GROUPS_FULL, privateGroupsOutOfUser))
        }

        if (authorityType == AuthorityType.GROUP) {
            predicate = AndPredicate.of(
                predicate,
                NotPredicate(
                    ValuePredicate(
                        "id",
                        ValuePredicate.Type.IN,
                        privateGroupsOutOfUser.map { it.getLocalId() }
                    )
                )
            )
        } else if (authorityType == AuthorityType.PERSON) {
            predicate = OrPredicate.of(
                ValuePredicate.contains(ATT_AUTHORITY_GROUPS_FULL, UNIFIED_PRIVATE_GROUP_REF),
                predicate
            )
        }
        return predicate
    }

    private fun preProcessPersonSortBy(sortBy: SortBy): SortBy {
        if (sortBy.attribute == PersonConstants.ATT_INACTIVITY_DAYS) {
            return SortBy(PersonConstants.ATT_LAST_ACTIVITY_TIME, !sortBy.ascending)
        }
        return sortBy
    }

    private fun preProcessPersonValuePredicate(pred: ValuePredicate): Predicate? {
        if (pred.getAttribute() == PersonConstants.ATT_INACTIVITY_DAYS) {
            return preProcessInactivityDaysPredicate(pred)
        }
        return pred
    }

    private fun preProcessInactivityDaysPredicate(pred: ValuePredicate): Predicate? {

        val daysCount = pred.getValue().asLong()
        val inactivityTime = Instant.now().minus(daysCount, ChronoUnit.DAYS)
        val inactivityTimeMinusDay = inactivityTime.minus(1, ChronoUnit.DAYS)

        val lastActivityTimeAtt = PersonConstants.ATT_LAST_ACTIVITY_TIME

        if (pred.getType() == ValuePredicate.Type.EQ) {
            return Predicates.and(
                Predicates.gt(lastActivityTimeAtt, inactivityTimeMinusDay),
                Predicates.lt(lastActivityTimeAtt, inactivityTime)
            )
        }

        return when (pred.getType()) {
            ValuePredicate.Type.GE -> Predicates.le(lastActivityTimeAtt, inactivityTime)
            ValuePredicate.Type.GT -> Predicates.le(lastActivityTimeAtt, inactivityTimeMinusDay)
            ValuePredicate.Type.LE -> Predicates.ge(lastActivityTimeAtt, inactivityTimeMinusDay)
            ValuePredicate.Type.LT -> Predicates.ge(lastActivityTimeAtt, inactivityTime)
            else -> null
        }
    }

    override fun mutateForAnyRes(records: List<LocalRecordAtts>): List<Any> {
        return mutate(records)
    }

    override fun mutate(records: List<LocalRecordAtts>): List<String> {
        if (syncService.isSyncContext()) {
            return super.mutate(records)
        }

        if (authorityType == AuthorityType.GROUP) {
            for (rec in records) {
                var currentGroupId = rec.id
                if (currentGroupId.isBlank()) {
                    currentGroupId = rec.attributes["id"].asText()
                }
                if (currentGroupId.isEmpty()) {
                    continue
                }
                var newGroups: Set<String> = rec.getAtt(ATT_AUTHORITY_GROUPS)
                    .toList(EntityRef::class.java)
                    .mapTo(HashSet()) { it.getLocalId() }
                if (newGroups.isEmpty()) {
                    newGroups = rec.getAtt("att_add_$ATT_AUTHORITY_GROUPS")
                        .toList(EntityRef::class.java)
                        .mapTo(HashSet()) { it.getLocalId() }
                }
                authorityService.integrityCheckBeforeAddToParents(currentGroupId, newGroups)
            }
        }

        val attsWithBlankId = records.filter {
            it.id.isBlank() && it.attributes["id"].asText().isBlank()
        }
        if (attsWithBlankId.isNotEmpty()) {
            error("Id field is missing for records: ${attsWithBlankId.map { it.id }}")
        }
        val result = ArrayList<String>()
        for (recordIt in records) {
            val record = prepareLocalAttsToMutation(recordIt)

            var id = record.id
            if (id.isBlank()) {
                id = record.attributes["id"].asText()
            }

            val currentAtts = getTargetAuthorityAtts(id)
            val exists = currentAtts.notExists != true

            var isManaged = false

            val authorityId = if (!exists) {
                if (!AuthoritiesSyncService.PROTECTED_FROM_SYNC_GROUPS.contains(id) &&
                    syncService.isNewAuthoritiesManaged(authorityType)
                ) {

                    isManaged = true
                    syncService.create(authorityType, record)
                } else {
                    super.mutate(listOf(record))[0]
                }
            } else if (EntityRef.isEmpty(currentAtts.managedBySync) ||
                !syncService.isSyncEnabled(currentAtts.managedBySync?.getLocalId())
            ) {

                super.mutate(listOf(record))[0]
            } else {
                isManaged = true
                syncService.update(currentAtts.managedBySync!!.getLocalId(), record)
            }
            result.add(authorityId)

            if (isManaged) {

                val attsAfterMutation = getTargetAuthorityAtts(id)
                val syncId = attsAfterMutation.managedBySync?.getLocalId()
                val managedAtts = syncService.getManagedAtts(syncId)

                val newAtts = ObjectData.create()
                record.attributes.forEach { k, v ->
                    if (!managedAtts.contains(k)) {
                        newAtts[k] = v
                    }
                }
                if (newAtts.size() > 0) {
                    super.mutate(listOf(LocalRecordAtts(authorityId, newAtts)))
                }
            }
        }

        return result
    }

    private fun getTargetAuthorityAtts(id: String): CurrentAuthorityAtts {
        return recordsService.getAtts(
            EntityRef.create(targetSourceId, id),
            CurrentAuthorityAtts::class.java
        )
    }

    private fun prepareLocalAttsToMutation(record: LocalRecordAtts): LocalRecordAtts {
        if (authorityType != AuthorityType.PERSON) {
            return record
        }

        if (record.id == PersonConstants.CURRENT_USER_ID) {
            return LocalRecordAtts(AuthContext.getCurrentUser(), record.attributes)
        }

        return record
    }

    private class CurrentAuthorityAtts(
        @AttName(RecordConstants.ATT_NOT_EXISTS)
        val notExists: Boolean? = null,
        val managedBySync: EntityRef? = null
    )
}
