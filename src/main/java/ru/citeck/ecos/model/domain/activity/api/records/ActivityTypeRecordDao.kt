package ru.citeck.ecos.model.domain.activity.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.model.lib.type.service.TypeRefService
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Component
class ActivityTypeRecordDao(
    private val typeRefService: TypeRefService
) : AbstractRecordsDao(), RecordsQueryDao {

    companion object {
        const val ID = "activity-type"
        private val ACTIVITY_TYPE_REF = ModelUtils.getTypeRef("ecos-activity")
        private val PLANNED_ACTIVITY_TYPE_REF = ModelUtils.getTypeRef("planned-activity")
        private val IMMEDIATE_ACTIVITY_TYPE_REF = ModelUtils.getTypeRef("immediate-activity")

        private val SYSTEM_ACTIVITIES_TYPE_REFS =
            listOf(ACTIVITY_TYPE_REF, PLANNED_ACTIVITY_TYPE_REF, IMMEDIATE_ACTIVITY_TYPE_REF)
    }

    override fun getId(): String {
        return ID
    }

    override fun queryRecords(recsQuery: RecordsQuery): Any? {
        val activityIds = typeRefService.expandWithChildren(ACTIVITY_TYPE_REF).toMutableList()
        activityIds.removeAll(SYSTEM_ACTIVITIES_TYPE_REFS)

        val activities = recordsService.getAtts(activityIds, ActivityDto::class.java)
        val query = recsQuery.getQueryOrNull(QueryDto::class.java)
        val typeRef = query?.typeRef ?: typeRefService.getTypeRef(query?.recordRef)
        if (typeRef.isNotEmpty()) {
            val result = activities.filter { activity ->
                val config = activity.config
                config == null || config.availableTypes.isEmpty() ||
                    config.availableTypes.find { typeRefService.isSubType(typeRef, it) } != null
            }.map { it.id }
            return result
        }
        return activityIds
    }

    private data class QueryDto(
        val recordRef: EntityRef? = null,
        val typeRef: EntityRef? = null
    )

    private data class ActivityDto(
        val id: EntityRef,
        @AttName("aspectById.activity-config.config?json")
        val config: ActivityConfig? = null
    )

    private data class ActivityConfig(
        val availableTypes: List<EntityRef> = emptyList()
    )
}
