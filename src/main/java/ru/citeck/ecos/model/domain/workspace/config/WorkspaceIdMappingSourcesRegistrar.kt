package ru.citeck.ecos.model.domain.workspace.config

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import ru.citeck.ecos.model.domain.workspace.api.records.WorkspaceProxyDao
import ru.citeck.ecos.records2.source.dao.local.job.JobsProvider
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.RecordsServiceFactory
import ru.citeck.ecos.records3.record.dao.RecordsDao

/**
 * Registers the records sources behind the workspace id mapping as soon as their beans exist,
 * without waiting for the common [ru.citeck.ecos.records3.RecordsDaoRegistrar], which runs only
 * after ALL records DAO beans are created. The registries (types, num templates) are initialized
 * while the context is still loading and map identifiers through this mapping; an unresolved answer
 * given in that window is kept by the caller-side cache of
 * [ru.citeck.ecos.model.lib.workspace.WorkspaceServiceImpl] long enough to poison the deferred sync
 * pass (COREDEV-550). Initializers which need the mapping must declare
 * `@DependsOn(WorkspaceIdMappingSourcesRegistrar.BEAN_NAME)`.
 *
 * The common registrar registers the same instances once more later; for these four DAOs that is
 * idempotent - the resolver overwrites the same entries and re-runs `setRecordsServiceFactory`,
 * the proxies have no stateful processor. Jobs are the exception: the resolver would schedule them
 * twice, so a DAO with jobs is rejected at startup.
 */
@Component(WorkspaceIdMappingSourcesRegistrar.BEAN_NAME)
class WorkspaceIdMappingSourcesRegistrar(
    private val recordsService: RecordsService,
    private val recordsServiceFactory: RecordsServiceFactory,
    @Qualifier("workspaceRepoDao") workspaceRepoDao: RecordsDao,
    workspaceProxyDao: WorkspaceProxyDao,
    @Qualifier("personRepo") personRepo: RecordsDao,
    @Qualifier("personDao") personDao: RecordsDao
) {

    companion object {
        const val BEAN_NAME = "workspaceIdMappingSourcesRegistrar"

        private val log = KotlinLogging.logger {}
    }

    // Proxies after their targets, so a lookup right after registration finds the whole chain.
    private val sources: List<RecordsDao> = listOf(workspaceRepoDao, workspaceProxyDao, personRepo, personDao)

    @PostConstruct
    fun init() {
        for (dao in sources) {
            // The resolver adds jobs on every registration without deduplication.
            check(dao !is JobsProvider || dao.jobs.isEmpty()) {
                "Records source '${dao.getId()}' provides jobs and can't be registered early: " +
                    "the common RecordsDaoRegistrar registers it once more and its jobs would be scheduled twice"
            }
            if (recordsServiceFactory.recordsResolver.getSourceInfo(dao.getId()) != null) {
                continue
            }
            log.info { "Register records source '${dao.getId()}' before the registries initialization" }
            recordsService.register(dao)
        }
    }
}
