package ru.citeck.ecos.model.domain.workspace.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.records.perms.DbPermsComponent
import ru.citeck.ecos.data.sql.records.perms.DbRecordPerms
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceVisitDesc
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.dao.RecordsDao

@Configuration
class WorkspaceVisitDaoConfig {

    @Bean
    fun workspaceVisitDao(
        dbDomainFactory: DbDomainFactory,
        workspaceDbPerms: WorkspaceDbPerms,
        recordsService: RecordsService
    ): RecordsDao {

        val typeRef = ModelUtils.getTypeRef(WorkspaceVisitDesc.TYPE_ID)

        val recordsDao = dbDomainFactory.create(
            DbDomainConfig.create()
                .withRecordsDao(
                    DbRecordsDaoConfig.create {
                        withId(WorkspaceVisitDesc.SOURCE_ID)
                        withTypeRef(typeRef)
                    }
                )
                .withDataService(
                    DbDataServiceConfig.create {
                        withTable("ecos_workspace_visit")
                        withStoreTableMeta(true)
                    }
                ).build()
        ).withSchema("ecos_data")
            .withPermsComponent(VisitPermsComponent(recordsService))
            .build()

        return recordsDao
    }

    private class VisitPermsComponent(
        private val recordsService: RecordsService
    ) : DbPermsComponent {

        override fun getRecordPerms(user: String, authorities: Set<String>, record: Any): DbRecordPerms {
            val recUser = recordsService.getAtt(
                record,
                WorkspaceVisitDesc.ATT_USER + ScalarType.LOCAL_ID_SCHEMA
            ).asText()
            return VisitPerms(user, recUser)
        }

        class VisitPerms(
            val authUser: String,
            val recUser: String
        ) : DbRecordPerms {

            override fun getAdditionalPerms(): Set<String> {
                return emptySet()
            }

            override fun getAuthoritiesWithReadPermission(): Set<String> {
                return setOf(recUser)
            }

            override fun hasAttReadPerms(name: String): Boolean {
                return authUser == recUser
            }

            override fun hasAttWritePerms(name: String): Boolean {
                return authUser == recUser
            }

            override fun hasReadPerms(): Boolean {
                return authUser == recUser
            }

            override fun hasWritePerms(): Boolean {
                return authUser == recUser
            }
        }
    }
}
