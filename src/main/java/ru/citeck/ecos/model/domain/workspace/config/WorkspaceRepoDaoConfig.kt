package ru.citeck.ecos.model.domain.workspace.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.model.domain.workspace.api.records.WorkspaceProxyDao.Companion.WORKSPACE_REPO_SOURCE_ID
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceMemberDesc
import ru.citeck.ecos.model.domain.workspace.listener.WorkspaceRecordsListener
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.records2.RecordConstants
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
        recordsService: RecordsService,
        workspaceService: WorkspaceService,
        recsListener: WorkspaceRecordsListener
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

        recordsDao.addAttributesMixin(DefaultWorkspaceMixin(recordsService, workspaceService))
        recordsDao.addListener(recsListener)

        return recordsDao
    }

    private class DefaultWorkspaceMixin(
        private val recordsService: RecordsService,
        private val workspaceService: WorkspaceService
    ) : AttMixin {

        val providedAtts = setOf(
            ScalarType.JSON.mirrorAtt,
            WorkspaceDesc.ATT_IS_CURRENT_USER_MEMBER,
            WorkspaceDesc.ATT_IS_CURRENT_USER_MANAGER,
            RecordConstants.ATT_WORKSPACE
        )

        override fun getAtt(path: String, value: AttValueCtx): Any? {

            return when (path) {
                WorkspaceDesc.ATT_IS_CURRENT_USER_MEMBER -> {
                    workspaceService.isUserMemberOf(AuthContext.getCurrentUser(), value.getLocalId())
                }
                WorkspaceDesc.ATT_IS_CURRENT_USER_MANAGER -> {
                    workspaceService.isUserManagerOf(AuthContext.getCurrentUser(), value.getLocalId())
                }
                RecordConstants.ATT_WORKSPACE -> {
                    value.getRef()
                }
                ScalarType.JSON.mirrorAtt -> {

                    val defaultJson = value.getAtt(ScalarType.JSON_SCHEMA)

                    val wsMembers = defaultJson[WorkspaceDesc.ATT_WORKSPACE_MEMBERS]

                    if (wsMembers.isArray() && wsMembers.size() > 0) {
                        val membersRefs = wsMembers.asList(EntityRef::class.java)
                        defaultJson[WorkspaceDesc.ATT_WORKSPACE_MEMBERS] = recordsService.getAtts(
                            membersRefs,
                            listOf(ScalarType.JSON_SCHEMA)
                        ).map { memberAtts ->
                            val memberData = memberAtts.getAtt(ScalarType.JSON_SCHEMA)
                            val memberId = memberData[WorkspaceMemberDesc.ATT_MEMBER_ID].asText()
                            if (memberId.isNotBlank()) {
                                memberData["id"] = memberId
                            }
                            memberData.remove(WorkspaceMemberDesc.ATT_MEMBER_ID)
                            // legacy attribute
                            memberData.remove("authority")
                            memberData
                        }
                    }

                    defaultJson
                }
                else -> null
            }
        }

        override fun getProvidedAtts(): Collection<String> {
            return providedAtts
        }
    }
}
