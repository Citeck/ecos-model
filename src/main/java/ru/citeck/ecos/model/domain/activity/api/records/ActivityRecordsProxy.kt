package ru.citeck.ecos.model.domain.activity.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.model.domain.activity.config.ActivityConfiguration
import ru.citeck.ecos.model.domain.comments.api.records.COMMENT_DAO_ID
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.PredicateUtils
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records2.predicate.model.ValuePredicate
import ru.citeck.ecos.records2.utils.ValWithIdx
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.atts.schema.resolver.AttContext
import ru.citeck.ecos.records3.record.atts.value.AttValue
import ru.citeck.ecos.records3.record.atts.value.AttValueProxy
import ru.citeck.ecos.records3.record.atts.value.impl.AttValueDelegate
import ru.citeck.ecos.records3.record.atts.value.impl.InnerAttValue
import ru.citeck.ecos.records3.record.dao.delete.DelStatus
import ru.citeck.ecos.records3.record.dao.impl.proxy.RecordsDaoProxy
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.entity.toEntityRef

@Component
class ActivityRecordsProxy : RecordsDaoProxy(
    ActivityConfiguration.ACTIVITY_DAO_ID,
    ActivityConfiguration.ACTIVITY_REPO_DAO_ID
) {

    companion object {
        const val COMMENT_ID_PREFIX = "comment$"
        private const val STATUS_COMPLETED = "completed"
    }

    override fun getRecordsAtts(recordIds: List<String>): List<*>? {

        val commentIds = mutableListOf<ValWithIdx<String>>()
        val activityIds = mutableListOf<ValWithIdx<String>>()

        recordIds.forEachIndexed { idx, recordId ->
            if (recordId.startsWith(COMMENT_ID_PREFIX)) {
                commentIds.add(ValWithIdx(recordId, idx))
            } else {
                activityIds.add(ValWithIdx(recordId, idx))
            }
        }

        val actResult = super.getRecordsAtts(activityIds.map { it.value })
            ?: error("getRecordAtts is null. Ids: $activityIds")

        if (commentIds.isEmpty()) {
            return actResult
        }

        val fullResult = ArrayList<ValWithIdx<*>>()
        actResult.forEachIndexed { idx, value ->
            fullResult.add(ValWithIdx(value, activityIds[idx].idx))
        }

        val schemaAtts = AttContext.getCurrentSchemaAtt().inner
        val writer = serviceFactory.attSchemaWriter
        val commentAttsToLoad = LinkedHashMap<String, String>()
        schemaAtts.forEach { att ->
            commentAttsToLoad[att.getAliasForValue()] = writer.write(att)
        }

        recordsService.getAtts(
            commentIds.map {
                EntityRef.create(AppName.EMODEL, COMMENT_DAO_ID, it.value.substringAfter('$'))
            },
            commentAttsToLoad,
            true
        ).forEachIndexed { idx, atts ->
            val idData = commentIds[idx]
            fullResult.add(
                ValWithIdx(
                    ProxyCommentVal(
                        EntityRef.create(AppName.EMODEL, ActivityConfiguration.ACTIVITY_DAO_ID, idData.value),
                        InnerAttValue(atts.getAtts().getData().asJson())
                    ),
                    idx
                )
            )
        }

        return fullResult.sortedBy { it.idx }.map { it.value }
    }

    override fun mutate(records: List<LocalRecordAtts>): List<String> {
        val (commentRecords, activityRecords) = records.partition { record ->
            record.id.startsWith(COMMENT_ID_PREFIX)
        }
        val result = super.mutate(activityRecords).toMutableList()

        if (commentRecords.isNotEmpty()) {
            val commentRecordsToMutate = commentRecords.map { record ->
                val atts = record.attributes
                atts.remove(RecordConstants.ATT_PARENT)
                atts.remove(RecordConstants.ATT_PARENT_ATT)
                RecordAtts(
                    EntityRef.Companion.create(AppName.EMODEL, COMMENT_DAO_ID, record.id.substringAfter('$')),
                    atts
                )
            }
            val ids = recordsService.mutate(commentRecordsToMutate).map { "$COMMENT_ID_PREFIX$it" }
            result.addAll(ids)
        }
        return result
    }

    override fun delete(recordIds: List<String>): List<DelStatus> {
        val (commentIds, activityIds) = recordIds.partition { id ->
            id.startsWith(COMMENT_ID_PREFIX)
        }
        val result = mutableListOf<DelStatus>()
        if (activityIds.isNotEmpty()) {
            for (activityId in activityIds) {
                val activityRef = EntityRef.create(AppName.EMODEL, ActivityConfiguration.ACTIVITY_DAO_ID, activityId)
                val activityData = recordsService.getAtts(activityRef, ActivityData::class.java)
                if (activityData.isPlannedActivity && STATUS_COMPLETED != activityData.status) {
                    cancelActivity(activityRef)
                }
            }
            result.addAll(super.delete(activityIds))
        }

        if (commentIds.isNotEmpty()) {
            val commentRecordsToDelete = commentIds.map { id ->
                EntityRef.create(AppName.EMODEL, COMMENT_DAO_ID, id.substringAfter('$'))
            }
            val delStatuses = recordsService.delete(commentRecordsToDelete)
            result.addAll(delStatuses)
        }
        return result
    }

    override fun queryRecords(recsQuery: RecordsQuery): RecsQueryRes<Any>? {

        if (recsQuery.language.isNotBlank() && recsQuery.language != PredicateService.LANGUAGE_PREDICATE) {
            return null
        }
        val predicate = recsQuery.getQuery(Predicate::class.java)
        var parentRef: EntityRef = EntityRef.EMPTY

        PredicateUtils.mapValuePredicates(predicate, { valuePred ->
            if (valuePred.getType() == ValuePredicate.Type.EQ &&
                valuePred.getAttribute() == RecordConstants.ATT_PARENT
            ) {
                parentRef = valuePred.getValue().asText().toEntityRef()
            }
            valuePred
        }, onlyAnd = true, optimize = false, filterEmptyComposite = false)

        if (AuthContext.isNotRunAsSystem()) {
            if (parentRef.isEmpty()) {
                return null
            }
            val canReadParent = recordsService.getAtt(parentRef, "permissions._has.read?bool!").asBoolean()
            if (!canReadParent) {
                return null
            }
        }

        val result = RecsQueryRes<Any>()
        val allRecords = mutableListOf<EntityRef>()

        val targetQuery = recsQuery.copy().withSourceId(getTargetId()).build()
        val queryRes = doWithSourceIdMapping {
            recordsService.query(targetQuery)
        }

        allRecords.addAll(queryRes.getRecords())
        result.setHasMore(queryRes.getHasMore())
        result.setTotalCount(queryRes.getTotalCount())

        if (parentRef.isNotEmpty()) {
            val comments = queryComments(parentRef)
            allRecords.addAll(comments)
            result.setTotalCount(result.getTotalCount() + comments.size)
        }

        val sortedRecords = predicateService.filterAndSort(
            allRecords,
            Predicates.alwaysTrue(),
            recsQuery.sortBy,
            recsQuery.page.skipCount,
            recsQuery.page.maxItems
        )
        result.setRecords(sortedRecords)
        return result
    }

    private fun queryComments(parentRef: EntityRef): List<EntityRef> {
        val comments = recordsService.query(
            RecordsQuery.create()
                .withSourceId("${AppName.EMODEL}/${COMMENT_DAO_ID}")
                .withLanguage(PredicateService.LANGUAGE_PREDICATE)
                .withQuery(
                    Predicates.and(
                        Predicates.eq("record", parentRef),
                        Predicates.eq(RecordConstants.ATT_PARENT, null)
                    )
                ).build()
        ).getRecords()
            .map { record ->
                EntityRef.create(
                    AppName.EMODEL,
                    ActivityConfiguration.ACTIVITY_DAO_ID,
                    "$COMMENT_ID_PREFIX${record.getLocalId()}"
                )
            }
        return comments
    }

    private fun cancelActivity(activityRef: EntityRef) {
        val cancelActivityAtts = recordsService.getAtts(
            activityRef,
            mapOf(
                "recordRef" to "?id",
                "responsibleEmail" to "responsible.email!",
                "participantsEmails" to "participants[].email!",
                "organizerEmail" to "_creator.email!",
                "activityDate" to "activityDate",
                "duration" to "activityDuration",
                "description" to "text",
                "calendarEventSummary" to "calendarEventSummary",
                "calendarEventUid" to "calendarEventUid",
                "calendarEventSequence" to "calendarEventSequence"
            )
        ).getAtts()

        recordsService.mutate(
            "emodel/cancel-activity@",
            cancelActivityAtts
        )
    }

    data class ActivityData(
        @AttName("_type.isSubTypeOf.planned-activity?bool!")
        val isPlannedActivity: Boolean,
        @AttName("_status?str")
        val status: String
    )

    private class ProxyCommentVal(
        private val id: EntityRef,
        base: AttValue
    ) : AttValueDelegate(base), AttValueProxy {

        override fun getId(): Any {
            return id
        }
    }
}
