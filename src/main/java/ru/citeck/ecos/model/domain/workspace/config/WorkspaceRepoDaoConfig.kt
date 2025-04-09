package ru.citeck.ecos.model.domain.workspace.config

import com.google.common.io.BaseEncoding
import com.google.common.primitives.Longs
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.model.domain.workspace.api.records.WorkspaceProxyDao.Companion.WORKSPACE_REPO_SOURCE_ID
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceMemberDesc
import ru.citeck.ecos.model.domain.workspace.dto.Workspace
import ru.citeck.ecos.model.domain.workspace.listener.WorkspaceRecordsListener
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.atts.value.AttValueCtx
import ru.citeck.ecos.records3.record.dao.RecordsDao
import ru.citeck.ecos.records3.record.mixin.AttMixin
import java.util.zip.CRC32
import kotlin.random.Random

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
                    val data = Json.mapper.toNonDefaultData(value.getAtts(Workspace::class.java))
                    val existingMemberIds = mutableSetOf<String>()
                    data[WorkspaceDesc.ATT_WORKSPACE_MEMBERS].forEach {
                        val memberId = it[WorkspaceMemberDesc.ATT_MEMBER_ID].asText()
                        if (memberId.isNotBlank()) {
                            existingMemberIds.add(memberId)
                        }
                    }
                    val newMembers = data[WorkspaceDesc.ATT_WORKSPACE_MEMBERS].mapNotNull { memberData ->
                        val id = memberData[WorkspaceMemberDesc.ATT_MEMBER_ID].asText().ifBlank {
                            generateSequence { generateMemberId() }.first { existingMemberIds.add(it) }
                        }
                        val newMemberData = DataValue.createObj().set("id", id)
                        memberData.forEach { k, v ->
                            if (k != WorkspaceMemberDesc.ATT_MEMBER_ID) {
                                newMemberData[k] = v
                            }
                        }
                        newMemberData
                    }
                    data[WorkspaceDesc.ATT_WORKSPACE_MEMBERS] = newMembers
                    data
                }

                else -> null
            }
        }

        private fun generateMemberId(): String {

            val crc = CRC32()
            val bytes = ByteArray(64)
            Random.nextBytes(bytes)
            crc.update(bytes)

            val id = BaseEncoding.base32().encode(Longs.toByteArray(crc.value))
            return id.lowercase().substringBefore('=').takeLast(6)
        }

        override fun getProvidedAtts(): Collection<String> {
            return providedAtts
        }
    }
}
