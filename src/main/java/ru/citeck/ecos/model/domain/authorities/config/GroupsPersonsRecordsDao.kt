package ru.citeck.ecos.model.domain.authorities.config

import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.model.domain.authsync.service.AuthoritiesSyncService
import ru.citeck.ecos.model.domain.authsync.service.AuthorityType
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.delete.DelStatus
import ru.citeck.ecos.records3.record.dao.impl.proxy.ProxyProcessor
import ru.citeck.ecos.records3.record.dao.impl.proxy.RecordsDaoProxy

class GroupsPersonsRecordsDao(
    id: String,
    private val authorityType: AuthorityType,
    private val syncService: AuthoritiesSyncService,
    proxyProcessor: ProxyProcessor? = null
) : RecordsDaoProxy(id, "$id-repo", proxyProcessor) {

    private val targetSourceId = "$id-repo"

    override fun delete(recordsId: List<String>): List<DelStatus> {
        error("Not supported")
    }

    override fun mutate(records: List<LocalRecordAtts>): List<String> {
        if (syncService.isSyncContext()) {
            return super.mutate(records)
        }
        if (!AuthContext.getCurrentAuthorities().contains(AuthRole.ADMIN)) {
            if (records.any {
                    it.id.isBlank()
                        || !it.id.contentEquals(AuthContext.getCurrentUser(), true)
            }) {
                error("Permission denied")
            }
        }

        val attsWithBlankId = records.filter {
            it.id.isBlank() && it.attributes.get("id").asText().isBlank()
        }
        if (attsWithBlankId.isNotEmpty()) {
            error("Id field is missing for ${getId()} record")
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

            if (!exists) {
                if (syncService.isNewAuthoritiesManaged(authorityType)) {
                    isManaged = true
                    result.add(syncService.create(authorityType, record))
                } else {
                    result.add(super.mutate(listOf(record))[0])
                }
            } else if (RecordRef.isEmpty(currentAtts.managedBySync)
                        || !syncService.isSyncEnabled(currentAtts.managedBySync?.id)) {

                result.add(super.mutate(listOf(record))[0])
            } else {
                isManaged = true
                result.add(syncService.update(currentAtts.managedBySync!!.id, record))
            }

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
                    super.mutate(listOf(LocalRecordAtts(id, newAtts)))
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
