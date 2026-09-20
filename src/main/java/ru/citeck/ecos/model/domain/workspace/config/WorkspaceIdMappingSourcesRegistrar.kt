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
 * Registers the records sources which back the workspace identifier mapping
 * ([ru.citeck.ecos.model.domain.workspace.service.EmodelWorkspaceService.getSystemId] and
 * [ru.citeck.ecos.model.domain.workspace.service.EmodelWorkspaceService.getWorkspaceIdBySystemId])
 * as soon as their beans exist, without waiting for the common
 * [ru.citeck.ecos.records3.RecordsDaoRegistrar], which runs only after ALL records DAO beans are created.
 *
 * The registries (types, num templates) are initialized while the application context is still
 * loading, and they map the identifier of every workspace-scoped artifact through this mapping. When
 * the sources are not registered yet, the mapping is reported as unresolved, and the unresolved answer
 * is kept by the caller-side cache of [ru.citeck.ecos.model.lib.workspace.WorkspaceServiceImpl] for its
 * unresolved TTL (30 seconds in ecos-model-lib 2.40), which covers the deferred sync pass: the
 * registries end up holding the artifacts under `DELETED_` identifiers and the records DAO of every
 * such type is not registered at all (COREDEV-550). Initializers which need the mapping must
 * declare `@DependsOn(WorkspaceIdMappingSourcesRegistrar.BEAN_NAME)`.
 *
 * The common [ru.citeck.ecos.records3.RecordsDaoRegistrar] registers the same DAO instances once more
 * later: the resolver overwrites the same entries and re-runs `setRecordsServiceFactory` on them. For
 * these four DAOs that is idempotent - [ru.citeck.ecos.data.sql.records.DbRecordsDao] rebuilds its
 * context from the same components, the proxies have no stateful processor, the virtual system user
 * record of 'person' is re-put into the same map. [ru.citeck.ecos.data.sql.records.DbRecordsDao] is a
 * [JobsProvider] whose jobs list is empty only while its table is not transactional, and the resolver
 * would schedule the jobs twice, so a DAO with jobs is rejected at startup. A DAO added to this list
 * later must keep these properties.
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

    // The proxies are listed after their targets, so a mapping lookup made right after this
    // registration finds the whole chain in place.
    private val sources: List<RecordsDao> = listOf(workspaceRepoDao, workspaceProxyDao, personRepo, personDao)

    @PostConstruct
    fun init() {
        for (dao in sources) {
            // The resolver adds the jobs of a DAO on every registration without deduplication, so a
            // DAO with jobs would run them twice after the common registrar. Fail fast instead.
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
