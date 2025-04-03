package ru.citeck.ecos.model.domain.admin.groupaction.execution

import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.data.sql.records.DbRecordsControlAtts
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.webapp.api.entity.EntityRef

class UpdateWorkspaceAdminAction(
    private val recordsService: RecordsService
) : AdminGroupActionBase<UpdateWorkspaceAdminAction.Config, UpdateWorkspaceAdminAction.ProcState>(
    "update-workspace"
) {
    override fun processImpl(state: ProcState, records: List<EntityRef>) {

        val mutAtts = records.map { entityRef ->
            RecordAtts(
                entityRef,
                ObjectData.create()
                    .set(DbRecordsControlAtts.UPDATE_WORKSPACE, state.targetWsId)
            )
        }
        recordsService.mutate(mutAtts)
    }

    override fun getState(config: Config): ProcState {
        if (config.targetWorkspace == null) {
            return ProcState(null)
        }
        return ProcState(EntityRef.valueOf(config.targetWorkspace).getLocalId())
    }

    class Config(
        val targetWorkspace: String?
    )

    class ProcState(
        val targetWsId: String?
    )
}
