package ru.citeck.ecos.model.domain.workspace.config

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import ru.citeck.ecos.model.domain.workspace.api.records.WorkspaceProxyDao
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
 * is kept by the caller-side cache of [ru.citeck.ecos.model.lib.workspace.WorkspaceServiceImpl] for
 * minutes: the registries end up holding the artifacts under `DELETED_` identifiers and the records DAO
 * of every such type is not registered at all (COREDEV-550). Initializers which need the mapping must
 * declare `@DependsOn(WorkspaceIdMappingSourcesRegistrar.BEAN_NAME)`.
 *
 * The common [ru.citeck.ecos.records3.RecordsDaoRegistrar] registers the same DAO instances once more
 * later: the resolver overwrites the same entries and re-runs `setRecordsServiceFactory` on them. For
 * these four DAOs that is idempotent - [ru.citeck.ecos.data.sql.records.DbRecordsDao] rebuilds its
 * context from the same components, the proxies have no stateful processor, the virtual system user
 * record of 'person' is re-put into the same map - and none of them provides jobs. A DAO added to
 * this list later must keep these properties.
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
            if (recordsServiceFactory.recordsResolver.getSourceInfo(dao.getId()) != null) {
                continue
            }
            log.info { "Register records source '${dao.getId()}' before the registries initialization" }
            recordsService.register(dao)
        }
    }
}
