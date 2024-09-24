package ru.citeck.ecos.model.domain.workspace.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.model.domain.workspace.api.records.WorkspaceProxyDao.Companion.WORKSPACE_REPO_SOURCE_ID
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records3.record.dao.RecordsDao

const val WORKSPACE_TYPE = "workspace"

@Configuration
class WorkspaceRepoDaoConfig(
    private val dbDomainFactory: DbDomainFactory,
) {

    @Bean
    fun workspaceRepoDao(workspaceDbPerms: WorkspaceDbPerms): RecordsDao {
        val workspaceTypeRef = ModelUtils.getTypeRef(WORKSPACE_TYPE)
        val recordsDao = dbDomainFactory.create(
            DbDomainConfig.create()
                .withRecordsDao(
                    DbRecordsDaoConfig.create {
                        withId(WORKSPACE_REPO_SOURCE_ID)
                        withTypeRef(workspaceTypeRef)
                    }
                )
                .withDataService(
                    DbDataServiceConfig.create {
                        withTable("ecos_workspace")
                        withStoreTableMeta(true)
                    }
                ).build()
        ).withSchema("public")
            .withPermsComponent(workspaceDbPerms)
            .build()

        return recordsDao
    }
}
