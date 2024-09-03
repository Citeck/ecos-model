package ru.citeck.ecos.model.domain.activity.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.model.lib.type.service.TypeRefService
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery

@Component
class ActivityTypeRecordDao(
    private val typeRefService: TypeRefService
) : AbstractRecordsDao(), RecordsQueryDao {

    companion object {
        const val ID = "activity-type"
        private val ACTIVITY_TYPE_REF = ModelUtils.getTypeRef("ecos-activity")
    }

    override fun getId(): String {
        return ID
    }

    override fun queryRecords(recsQuery: RecordsQuery): Any? {
        val result = typeRefService.expandWithChildren(ACTIVITY_TYPE_REF).toMutableList()
        result.remove(ACTIVITY_TYPE_REF)
        return result
    }
}
