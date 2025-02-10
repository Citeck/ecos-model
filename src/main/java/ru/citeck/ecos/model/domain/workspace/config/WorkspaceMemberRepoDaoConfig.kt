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
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.atts.value.AttValueCtx
import ru.citeck.ecos.records3.record.dao.RecordsDao
import ru.citeck.ecos.records3.record.mixin.AttMixin

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

        recordsDao.addAttributesMixin(DefaultWorkspaceMemberMixin())

        return recordsDao
    }

    private class DefaultWorkspaceMemberMixin : AttMixin {

        val providedAtts = setOf(
            ScalarType.JSON.mirrorAtt
        )

        override fun getAtt(path: String, value: AttValueCtx): Any? {

            return when (path) {
                ScalarType.JSON.mirrorAtt -> {
                    val memberData = value.getAtt(ScalarType.JSON_SCHEMA)
                    val memberId = memberData[WorkspaceMemberDesc.ATT_MEMBER_ID].asText()
                    if (memberId.isNotBlank()) {
                        memberData["id"] = memberId
                    }
                    memberData.remove(WorkspaceMemberDesc.ATT_MEMBER_ID)
                    // legacy attribute
                    memberData.remove("authority")
                    memberData
                }
                else -> null
            }
        }

        override fun getProvidedAtts(): Collection<String> {
            return providedAtts
        }
    }
}
