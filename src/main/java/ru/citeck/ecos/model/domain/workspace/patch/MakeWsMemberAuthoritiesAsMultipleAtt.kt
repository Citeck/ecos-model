package ru.citeck.ecos.model.domain.workspace.patch

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceMemberDesc
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.patch.annotaion.EcosPatch
import java.util.concurrent.Callable

@Component
@EcosPatch("make-ws-member-authorities-as-multiple-att", "2025-02-10T00:00:00Z")
class MakeWsMemberAuthoritiesAsMultipleAtt(
    val recordsService: RecordsService
) : Callable<Any> {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun call(): Any {

        val workspacesToUpdate = recordsService.query(RecordsQuery.create()
            .withSourceId(WorkspaceMemberDesc.SOURCE_ID)
            .withQuery(Predicates.empty(WorkspaceMemberDesc.ATT_AUTHORITIES))
            .build(),
            WorkspaceMemberAtts::class.java
        ).getRecords()

        if (workspacesToUpdate.isEmpty()) {
            return "[]"
        }

        log.info { "Workspaces to update: $workspacesToUpdate" }

        for (workspace in workspacesToUpdate) {
            if (EntityRef.isNotEmpty(workspace.authority)) {
                recordsService.mutateAtt(
                    workspace.id,
                    WorkspaceMemberDesc.ATT_AUTHORITIES,
                    listOf(workspace.authority)
                )
            } else {
                log.warn { "Authority att is empty for ${workspace.id}" }
            }
        }

        log.info { "Updating completed" }
        return "[" + workspacesToUpdate.joinToString { "\"$it\""} + "]"
    }

    data class WorkspaceMemberAtts(
        val id: EntityRef,
        val authority: EntityRef?
    )
}
