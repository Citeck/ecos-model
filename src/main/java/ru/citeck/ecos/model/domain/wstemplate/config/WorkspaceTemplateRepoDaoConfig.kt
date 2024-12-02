package ru.citeck.ecos.model.domain.wstemplate.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.io.file.mem.EcosMemDir
import ru.citeck.ecos.commons.json.YamlUtils
import ru.citeck.ecos.commons.utils.ZipUtils
import ru.citeck.ecos.context.lib.auth.AuthGroup
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.records.perms.DbPermsComponent
import ru.citeck.ecos.data.sql.records.perms.DbRecordPerms
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.model.domain.wstemplate.desc.WorkspaceTemplateDesc
import ru.citeck.ecos.model.domain.wstemplate.listener.WorkspaceTemplateRecordsListener
import ru.citeck.ecos.model.domain.wstemplate.service.WorkspaceTemplateService
import ru.citeck.ecos.model.lib.attributes.dto.AttributeType
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.atts.value.AttValueCtx
import ru.citeck.ecos.records3.record.dao.RecordsDao
import ru.citeck.ecos.records3.record.dao.impl.proxy.RecordsDaoProxy
import ru.citeck.ecos.records3.record.mixin.AttMixin
import ru.citeck.ecos.webapp.api.entity.toEntityRef
import ru.citeck.ecos.webapp.lib.model.type.registry.EcosTypesRegistry
import java.io.ByteArrayInputStream
import java.util.*
import kotlin.collections.LinkedHashMap

@Configuration
class WorkspaceTemplateRepoDaoConfig {

    companion object {
        private const val REPO_SRC_ID = WorkspaceTemplateDesc.SOURCE_ID + "-repo"

        private val META_EXCLUDED_ATT_TYPES_FOR_CONTENT = listOf(
            AttributeType.BINARY
        )
    }

    @Bean
    fun workspaceTemplateDao(templateService: WorkspaceTemplateService): RecordsDao {
        return object : RecordsDaoProxy(WorkspaceTemplateDesc.SOURCE_ID, REPO_SRC_ID) {

            override fun mutate(records: List<LocalRecordAtts>): List<String> {
                return super.mutate(records.map { preProcessTemplateMut(it) })
            }

            private fun preProcessTemplateMut(record: LocalRecordAtts): LocalRecordAtts {
                if (!record.attributes.has(WorkspaceTemplateDesc.ATT_WORKSPACE_REF)) {
                    return record
                }
                val workspaceRef = record.attributes[WorkspaceTemplateDesc.ATT_WORKSPACE_REF].asText().toEntityRef()
                val artifacts = templateService.getWorkspaceArtifactsForTemplate(workspaceRef.getLocalId())

                val newAtts = record.deepCopy()
                newAtts.setAtt(WorkspaceTemplateDesc.ATT_ARTIFACTS, ZipUtils.writeZipAsBytes(artifacts))

                return newAtts
            }
        }
    }

    @Bean
    fun workspaceTemplateRepoDao(
        dbDomainFactory: DbDomainFactory,
        recordsService: RecordsService,
        typesRegistry: EcosTypesRegistry,
        recsListener: WorkspaceTemplateRecordsListener
    ): RecordsDao {

        val workspaceTypeRef = ModelUtils.getTypeRef(WorkspaceTemplateDesc.TYPE_ID)

        val recordsDao = dbDomainFactory.create(
            DbDomainConfig.create()
                .withRecordsDao(
                    DbRecordsDaoConfig.create {
                        withId(REPO_SRC_ID)
                        withTypeRef(workspaceTypeRef)
                    }
                )
                .withDataService(
                    DbDataServiceConfig.create {
                        withTable("ecos_workspace_template")
                        withStoreTableMeta(true)
                    }
                ).build()
        ).withSchema("ecos_data")
            .withPermsComponent(object : DbPermsComponent {
                override fun getRecordPerms(user: String, authorities: Set<String>, record: Any): DbRecordPerms {
                    val isAdmin = authorities.contains(AuthRole.ADMIN)

                    return object : DbRecordPerms {
                        override fun getAdditionalPerms(): Set<String> {
                            return emptySet()
                        }
                        override fun getAuthoritiesWithReadPermission(): Set<String> {
                            return setOf(AuthGroup.EVERYONE)
                        }
                        override fun hasReadPerms(): Boolean {
                            return true
                        }
                        override fun hasWritePerms(): Boolean {
                            return isAdmin
                        }
                        override fun hasAttWritePerms(name: String): Boolean {
                            return isAdmin
                        }
                        override fun hasAttReadPerms(name: String): Boolean {
                            return true
                        }
                    }
                }
            })
            .build()

        recordsDao.addAttributesMixin(WorkspaceTemplateMixin(typesRegistry))
        recordsDao.addListener(recsListener)

        return recordsDao
    }

    private class WorkspaceTemplateMixin(
        private val typesRegistry: EcosTypesRegistry
    ) : AttMixin {

        val providedAtts = setOf("data", "meta")

        override fun getAtt(path: String, value: AttValueCtx): Any? {
            return when (path) {
                "data" -> getData(value)
                "meta" -> getMeta(value)
                else -> return null
            }
        }

        private fun getMeta(value: AttValueCtx): ObjectData {
            val typeDef = typesRegistry.getValue(WorkspaceTemplateDesc.TYPE_ID)
                ?: error("Type is not found by id ${WorkspaceTemplateDesc.TYPE_ID}")

            val attsForMeta = LinkedHashMap<String, String>()
            typeDef.model.attributes.forEach {
                if (!META_EXCLUDED_ATT_TYPES_FOR_CONTENT.contains(it.type)) {
                    attsForMeta[it.id] = (it.id + ScalarType.RAW_SCHEMA)
                }
            }
            return value.getAtts(attsForMeta)
        }

        private fun getData(value: AttValueCtx): ByteArray {

            val result = EcosMemDir()
            val artifactsDir = result.createDir("artifacts")
            val artifactsBase64 = value.getAtt("artifacts?str").asText()
            if (artifactsBase64.isNotBlank()) {
                ZipUtils.extractZip(
                    ByteArrayInputStream(Base64.getDecoder().decode(artifactsBase64)),
                    artifactsDir
                )
            }
            result.createFile(
                "meta.yml",
                YamlUtils.toString(getMeta(value))
            )
            return ZipUtils.writeZipAsBytes(result)
        }

        override fun getProvidedAtts(): Collection<String> {
            return providedAtts
        }
    }
}
