package ru.citeck.ecos.model.domain.workspace.patch

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.data.sql.records.dao.atts.DbRecord
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.domain.workspace.listener.WorkspaceRecordsListener
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.txn.lib.TxnContext
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.patch.annotaion.EcosPatch
import java.util.concurrent.Callable

@Component
@EcosPatch("fill-visible-in-workspaces-for-managed-records", "2026-03-17T00:00:00Z")
class FillVisibleInWorkspacesForManagedRecords(
    val recordsService: RecordsService
) : Callable<Any> {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun call(): Any {

        val workspacesWithManagedBy = recordsService.query(
            RecordsQuery.create()
                .withSourceId(WorkspaceDesc.SOURCE_ID)
                .withQuery(
                    Predicates.notEmpty(WorkspaceRecordsListener.ATT_WORKSPACE_MANAGED_BY)
                )
                .build(),
            WsWithManagedBy::class.java
        ).getRecords()

        if (workspacesWithManagedBy.isEmpty()) {
            log.info { "No workspaces with workspaceManagedBy found" }
            return "[]"
        }

        log.info { "Found ${workspacesWithManagedBy.size} workspace(s) with workspaceManagedBy" }

        val updatedTargets = mutableListOf<String>()
        for (ws in workspacesWithManagedBy) {
            val managedBy = ws.managedBy
            if (managedBy == null || managedBy.isEmpty()) {
                continue
            }
            TxnContext.doInNewTxn {
                recordsService.mutateAtt(
                    managedBy,
                    "att_add_${DbRecord.ATT_VISIBLE_IN_WORKSPACES}",
                    ws.wsRef
                )
            }
            updatedTargets.add("${ws.wsRef} -> $managedBy")
        }

        log.info { "Updated ${updatedTargets.size} record(s): $updatedTargets" }
        return updatedTargets
    }

    private data class WsWithManagedBy(
        @AttName("?id")
        val wsRef: EntityRef,
        @AttName("${WorkspaceRecordsListener.ATT_WORKSPACE_MANAGED_BY}?id")
        val managedBy: EntityRef?
    )
}
