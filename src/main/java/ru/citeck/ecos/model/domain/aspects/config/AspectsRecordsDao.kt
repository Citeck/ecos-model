package ru.citeck.ecos.model.domain.aspects.config

import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.dao.delete.DelStatus
import ru.citeck.ecos.records3.record.dao.impl.proxy.ProxyProcessor
import ru.citeck.ecos.records3.record.dao.impl.proxy.RecordsDaoProxy
import ru.citeck.ecos.records3.record.dao.mutate.RecordsMutateWithAnyResDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes

open class AspectsRecordsDao(
    id: String,
    targetId: String,
    proxyProcessor: ProxyProcessor? = null
) : RecordsDaoProxy(id, targetId, proxyProcessor), RecordsMutateWithAnyResDao {

    override fun mutateForAnyRes(records: List<LocalRecordAtts>): List<Any> {
        records.forEach {
            val prefix = it.attributes["prefix"].asText()
            val existingRef = recordsService.queryOne(
                RecordsQuery.create {
                    withSourceId("emodel/aspect")
                    withQuery(Predicates.eq("prefix", prefix))
                }
            )
            if (existingRef != null && existingRef.isNotEmpty()) {
                val recordId = it.attributes["id"].asText().ifBlank { it.id }
                if (recordId != existingRef.id) {
                    error("Prefix already used by aspect ${existingRef.id}")
                }
            }
        }
        return mutate(records)
    }

    override fun delete(recordIds: List<String>): List<DelStatus> {
        return super.delete(recordIds)
    }

    override fun getRecordsAtts(recordIds: List<String>): List<*>? {
        return super.getRecordsAtts(recordIds)
    }

    override fun queryRecords(recsQuery: RecordsQuery): RecsQueryRes<*>? {
        return super.queryRecords(recsQuery)
    }

    override fun mutate(records: List<LocalRecordAtts>): List<String> {
        return super.mutate(records)
    }
}
