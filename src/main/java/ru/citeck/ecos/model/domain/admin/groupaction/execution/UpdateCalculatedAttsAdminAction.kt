package ru.citeck.ecos.model.domain.admin.groupaction.execution

import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.data.sql.records.DbRecordsControlAtts
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.webapp.api.entity.EntityRef

class UpdateCalculatedAttsAdminAction(
    private val recordsService: RecordsService
) : AdminGroupActionBase<Unit, Unit>(
    "update-calculated-atts"
) {
    override fun processImpl(state: Unit, records: List<EntityRef>) {

        val mutAtts = records.map { entityRef ->
            RecordAtts(
                entityRef,
                ObjectData.create()
                    .set(DbRecordsControlAtts.UPDATE_CALCULATED_ATTS, true)
            )
        }
        recordsService.mutate(mutAtts)
    }

    override fun getState(config: Unit) = Unit
}
