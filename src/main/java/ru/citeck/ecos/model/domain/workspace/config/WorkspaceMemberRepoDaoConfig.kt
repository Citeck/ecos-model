package ru.citeck.ecos.model.domain.workspace.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.model.domain.workspace.api.records.WorkspaceMemberProxyDao.Companion.WORKSPACE_MEMBER_REPO_SOURCE_ID
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceMemberDesc
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records3.record.dao.RecordsDao

@Configuration
class WorkspaceMemberRepoDaoConfig(
    private val dbDomainFactory: DbDomainFactory
) {

    @Bean
    fun workspaceMemberRepoDao(): RecordsDao {
        val workspaceMemberTypeRef = ModelUtils.getTypeRef(WorkspaceMemberDesc.TYPE_ID)
        val recordsDao = dbDomainFactory.create(
            DbDomainConfig.create()
                .withRecordsDao(
                    DbRecordsDaoConfig.create {
                        withId(WORKSPACE_MEMBER_REPO_SOURCE_ID)
                        withTypeRef(workspaceMemberTypeRef)
                    }
                )
                .withDataService(
                    DbDataServiceConfig.create {
                        withTable("ecos_workspace_member")
                        withStoreTableMeta(true)
                    }
                ).build()
        ).withSchema("ecos_data")
            .build()

        return recordsDao
    }
}
