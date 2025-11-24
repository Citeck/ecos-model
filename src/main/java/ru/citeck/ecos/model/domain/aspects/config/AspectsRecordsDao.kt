package ru.citeck.ecos.model.domain.aspects.config

import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.dao.impl.proxy.ProxyProcessor
import ru.citeck.ecos.records3.record.dao.impl.proxy.RecordsDaoProxy
import ru.citeck.ecos.records3.record.dao.mutate.RecordsMutateWithAnyResDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery

open class AspectsRecordsDao(
    id: String,
    targetId: String,
    proxyProcessor: ProxyProcessor? = null
) : RecordsDaoProxy(id, targetId, proxyProcessor), RecordsMutateWithAnyResDao {

    override fun mutateForAnyRes(records: List<LocalRecordAtts>): List<Any> {
        val newRecs = records.map {
            check(AuthContext.isRunAsSystemOrAdmin()) {
                "Permission denied"
            }

            val recordId = it.attributes["id"].asText().ifBlank { it.id }
            if (recordId.isBlank()) {
                error("Id is blank")
            }
            val prefix = it.attributes["prefix"].asText().ifBlank { recordId }
            val existingRef = AuthContext.runAsSystem {
                recordsService.queryOne(
                    RecordsQuery.create {
                        withEcosType("aspect")
                        withQuery(
                            Predicates.or(
                                Predicates.eq("prefix", prefix),
                                Predicates.and(
                                    Predicates.empty("prefix"),
                                    Predicates.eq("id", prefix)
                                )
                            )
                        )
                    }
                )
            }
            if (existingRef != null && existingRef.isNotEmpty()) {
                if (recordId != existingRef.getLocalId()) {
                    val existingAspectName = AuthContext.runAsSystem {
                        recordsService.getAtt(existingRef, ScalarType.DISP_SCHEMA).asText()
                    }
                    error("Prefix already used by aspect '$existingAspectName (${existingRef.getLocalId()})'")
                }
            }
            val newAtts = it.getAtts().deepCopy()
            for (attWithAtts in listOf("attributes", "systemAttributes")) {
                if (it.attributes.has(attWithAtts)) {
                    val resAtts = DataValue.createArr()
                    it.attributes[attWithAtts].forEach { att ->
                        if (att["id"].asText().isNotBlank()) {
                            resAtts.add(att)
                        }
                    }
                    newAtts[attWithAtts] = resAtts
                }
            }
            LocalRecordAtts(it.id, newAtts)
        }
        return mutate(newRecs)
    }
}
