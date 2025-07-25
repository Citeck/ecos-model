package ru.citeck.ecos.model.domain.workspace.patch

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.domain.workspace.listener.WorkspaceRecordsListener
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.lib.patch.annotaion.EcosPatch
import java.util.concurrent.Callable

@Component
@EcosPatch("create-wiki-root-for-workspaces", "2025-01-05T00:00:02Z")
class CreateWikiRootForWorkspaces(
    val recordsService: RecordsService,
    val workspaceRecsListener: WorkspaceRecordsListener
) : Callable<Any> {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun call(): Any {

        val workspacesToUpdate = recordsService.query(
            RecordsQuery.create()
                .withSourceId(WorkspaceDesc.SOURCE_ID)
                .withQuery(Predicates.empty("wikiRoot"))
                .build()
        ).getRecords()

        if (workspacesToUpdate.isEmpty()) {
            return "[]"
        }

        log.info { "Workspaces to update: $workspacesToUpdate" }

        for (workspace in workspacesToUpdate) {
            workspaceRecsListener.createWikiRoot(workspace)
        }

        log.info { "Updating completed" }
        return "[" + workspacesToUpdate.joinToString { "\"$it\"" } + "]"
    }
}
