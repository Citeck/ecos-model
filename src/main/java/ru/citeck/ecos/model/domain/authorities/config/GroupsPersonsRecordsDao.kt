package ru.citeck.ecos.model.domain.authorities.config

import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityConstants.ATT_AUTHORITY_GROUPS
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityConstants.ATT_AUTHORITY_GROUPS_FULL
import ru.citeck.ecos.model.domain.authorities.service.AuthorityService
import ru.citeck.ecos.model.domain.authsync.service.AuthoritiesSyncService
import ru.citeck.ecos.model.domain.authsync.service.AuthorityType
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.PredicateUtils
import ru.citeck.ecos.records2.predicate.model.OrPredicate
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records2.predicate.model.ValuePredicate
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.delete.DelStatus
import ru.citeck.ecos.records3.record.dao.impl.proxy.ProxyProcessor
import ru.citeck.ecos.records3.record.dao.impl.proxy.RecordsDaoProxy
import ru.citeck.ecos.records3.record.dao.mutate.RecordsMutateWithAnyResDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes

class GroupsPersonsRecordsDao(
    id: String,
    private val authorityType: AuthorityType,
    private val syncService: AuthoritiesSyncService,
    private val authorityService: AuthorityService,
    proxyProcessor: ProxyProcessor? = null
) : RecordsDaoProxy(id, "$id-repo", proxyProcessor), RecordsMutateWithAnyResDao {

    private val targetSourceId = "$id-repo"

    override fun delete(recordsId: List<String>): List<DelStatus> {
        error("Not supported")
    }

    override fun getRecordsAtts(recordsId: List<String>): List<*>? {
        if (authorityType == AuthorityType.PERSON) {
            return super.getRecordsAtts(recordsId.map {
                if (it == "CURRENT") {
                    AuthContext.getCurrentUser()
                } else {
                    it
                }
            })
        }
        return super.getRecordsAtts(recordsId)
    }

    override fun queryRecords(recsQuery: RecordsQuery): RecsQueryRes<*>? {
        if (recsQuery.language != PredicateService.LANGUAGE_PREDICATE) {
            return super.queryRecords(recsQuery)
        }
        val predicate = PredicateUtils.mapValuePredicates(recsQuery.getQuery(Predicate::class.java)) { pred ->

            if (pred.getType() == ValuePredicate.Type.CONTAINS && pred.getAttribute() == ATT_AUTHORITY_GROUPS_FULL) {
                val values = pred.getValue().toList(RecordRef::class.java)
                val expandedGroups = authorityService.getExpandedGroups(values.map { it.id }, false)
                OrPredicate.of(expandedGroups.map {
                    Predicates.contains(ATT_AUTHORITY_GROUPS, AuthorityType.GROUP.getRef(it).toString())
                })
            } else {
                pred
            }
        }
        return super.queryRecords(recsQuery.copy {
            withQuery(predicate)
        })
    }

    override fun mutateForAnyRes(records: List<LocalRecordAtts>): List<Any> {
        val result = mutate(records)
        if (authorityType == AuthorityType.PERSON) {
            return result.map {
                // todo: remove after user dashboard will be migrated to emodel
                RecordRef.create("alfresco", "people", it)
            }
        }
        return result
    }

    private fun permissionDenied() {
        error("Permission denied")
    }

    override fun mutate(records: List<LocalRecordAtts>): List<String> {
        if (syncService.isSyncContext()) {
            return super.mutate(records)
        }
        if (!AuthContext.isRunAsSystem() && !AuthContext.getCurrentAuthorities().contains(AuthRole.ADMIN)) {
            if (records.any { it.id.isBlank() }) {
                permissionDenied()
            }
            if (authorityType == AuthorityType.PERSON) {
                val currentUser = AuthContext.getCurrentUser()
                for (record in records) {
                    if (record.id == currentUser) {
                        record.attributes.forEach { key, _ ->
                            if (key.contains(ATT_AUTHORITY_GROUPS)) {
                                // user can mutate own attributes,
                                // but groups should be changed only by admin or system
                                permissionDenied()
                            }
                        }
                    } else {
                        permissionDenied()
                    }
                }
            } else {
                permissionDenied()
            }
        }

        if (authorityType == AuthorityType.GROUP) {
            for (rec in records) {
                var currentGroupId = rec.id
                if (currentGroupId.isBlank()) {
                    currentGroupId = rec.attributes.get("id").asText()
                }
                if (currentGroupId.isEmpty()) {
                    continue
                }
                var newGroups: List<String> = rec.getAtt(ATT_AUTHORITY_GROUPS)
                    .toList(RecordRef::class.java)
                    .map { it.id }
                if (newGroups.isEmpty()) {
                    newGroups = rec.getAtt("att_add_$ATT_AUTHORITY_GROUPS")
                        .toList(RecordRef::class.java)
                        .map { it.id }
                }
                for (newGroup in newGroups) {
                    val expandedGroups = authorityService.getExpandedGroups(newGroup, true)
                    if (expandedGroups.contains(currentGroupId)) {
                        error("Cyclic dependency. Group '${currentGroupId}' can't be added to group: $newGroup")
                    }
                }
            }
        }

        val attsWithBlankId = records.filter {
            it.id.isBlank() && it.attributes.get("id").asText().isBlank()
        }
        if (attsWithBlankId.isNotEmpty()) {
            error("Id field is missing for records: ${attsWithBlankId.map { it.id }}")
        }
        val result = ArrayList<String>()
        for (record in records) {

            var id = record.id
            if (id.isBlank()) {
                id = record.attributes.get("id").asText()
            }

            val currentAtts = getTargetAuthorityAtts(id)
            val exists = currentAtts.notExists != true

            var isManaged = false

            val authorityId = if (!exists) {
                if (syncService.isNewAuthoritiesManaged(authorityType)) {
                    isManaged = true
                    syncService.create(authorityType, record)
                } else {
                    super.mutate(listOf(record))[0]
                }
            } else if (RecordRef.isEmpty(currentAtts.managedBySync)
                        || !syncService.isSyncEnabled(currentAtts.managedBySync?.id)) {

                super.mutate(listOf(record))[0]
            } else {
                isManaged = true
                syncService.update(currentAtts.managedBySync!!.id, record)
            }
            result.add(authorityId)

            if (isManaged) {

                val attsAfterMutation = getTargetAuthorityAtts(id)
                val syncId = attsAfterMutation.managedBySync?.id
                val managedAtts = syncService.getManagedAtts(syncId)

                val newAtts = ObjectData.create()
                record.attributes.forEach { k, v ->
                    if (!managedAtts.contains(k)) {
                        newAtts.set(k, v)
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
            RecordRef.create(targetSourceId, id),
            CurrentAuthorityAtts::class.java
        )
    }

    private class CurrentAuthorityAtts(
        @AttName(RecordConstants.ATT_NOT_EXISTS)
        val notExists: Boolean? = null,
        val managedBySync: RecordRef? = null
    )
}
