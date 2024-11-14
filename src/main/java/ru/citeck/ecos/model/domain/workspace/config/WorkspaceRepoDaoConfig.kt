package ru.citeck.ecos.model.domain.workspace.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.model.domain.workspace.api.records.WorkspaceProxyDao.Companion.WORKSPACE_REPO_SOURCE_ID
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.atts.value.AttValueCtx
import ru.citeck.ecos.records3.record.dao.RecordsDao
import ru.citeck.ecos.records3.record.mixin.AttMixin
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Configuration
class WorkspaceRepoDaoConfig {

    @Bean
    fun workspaceRepoDao(
        dbDomainFactory: DbDomainFactory,
        workspaceDbPerms: WorkspaceDbPerms,
        recordsService: RecordsService
    ): RecordsDao {

        val workspaceTypeRef = ModelUtils.getTypeRef(WorkspaceDesc.TYPE_ID)

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
        ).withSchema("ecos_data")
            .withPermsComponent(workspaceDbPerms)
            .build()

        recordsDao.addAttributesMixin(DefaultWorkspaceMixin(recordsService))

        return recordsDao
    }

    private class DefaultWorkspaceMixin(
        private val recordsService: RecordsService
    ) : AttMixin {

        val providedAtts = setOf(ScalarType.JSON.mirrorAtt)

        override fun getAtt(path: String, value: AttValueCtx): Any {

            val defaultJson = value.getAtt(ScalarType.JSON_SCHEMA)

            val wsMembers = defaultJson[WorkspaceDesc.ATT_WORKSPACE_MEMBERS]

            if (wsMembers.isArray() && wsMembers.size() > 0) {
                val membersRefs = wsMembers.asList(EntityRef::class.java)
                defaultJson[WorkspaceDesc.ATT_WORKSPACE_MEMBERS] = recordsService.getAtts(
                    membersRefs,
                    listOf(ScalarType.JSON_SCHEMA)
                ).map { it.getAtt(ScalarType.JSON_SCHEMA) }
            }

            return defaultJson
        }

        override fun getProvidedAtts(): Collection<String> {
            return providedAtts
        }
    }
}
