package ru.citeck.ecos.model.domain.aspects.config

import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef
import ru.citeck.ecos.model.service.validation.ModelAttColumnNameValidator
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.dao.impl.proxy.ProxyProcessor
import ru.citeck.ecos.records3.record.dao.impl.proxy.RecordsDaoProxy
import ru.citeck.ecos.records3.record.dao.mutate.RecordsMutateWithAnyResDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef

open class AspectsRecordsDao(
    id: String,
    targetId: String,
    proxyProcessor: ProxyProcessor? = null
) : RecordsDaoProxy(id, targetId, proxyProcessor),
    RecordsMutateWithAnyResDao {

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
            ModelAttColumnNameValidator.validateAspectAtts(
                recordId,
                prefixToValidate(it, prefix, recordId),
                attsToValidate(it, newAtts, recordId, "attributes"),
                attsToValidate(it, newAtts, recordId, "systemAttributes")
            )
            LocalRecordAtts(it.id, newAtts)
        }
        return mutate(newRecs)
    }

    /**
     * Prefix under which the attributes will be stored. A payload that omits the prefix keeps the
     * stored one, so an existing aspect is validated against it and not against the aspect id the
     * uniqueness check above falls back to. A blank stored prefix means the aspect id, as in AspectDef.
     */
    private fun prefixToValidate(record: LocalRecordAtts, payloadPrefix: String, recordId: String): String {
        if (record.attributes.has("prefix")) {
            return payloadPrefix
        }
        val storedPrefix = AuthContext.runAsSystem {
            recordsService.getAtt(aspectRef(recordId), "prefix").asText()
        }
        return storedPrefix.ifBlank { recordId }
    }

    /**
     * Attributes whose column names must fit the storage limit under the prefix being saved.
     * When the payload changes the prefix but omits a list, the stored list is validated instead:
     * a new prefix renames every column of the aspect.
     */
    private fun attsToValidate(
        record: LocalRecordAtts,
        newAtts: ObjectData,
        recordId: String,
        listAtt: String
    ): List<AttributeDef> {
        if (record.attributes.has(listAtt)) {
            return newAtts[listAtt].asList(AttributeDef::class.java)
        }
        if (!record.attributes.has("prefix")) {
            return emptyList()
        }
        return AuthContext.runAsSystem {
            recordsService.getAtt(aspectRef(recordId), "$listAtt[]?json").asList(AttributeDef::class.java)
        }
    }

    private fun aspectRef(recordId: String): EntityRef {
        return EntityRef.create(AppName.EMODEL, getId(), recordId)
    }
}
