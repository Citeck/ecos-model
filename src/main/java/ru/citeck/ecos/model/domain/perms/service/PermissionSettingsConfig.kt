package ru.citeck.ecos.model.domain.perms.service

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.records.listener.DbRecordChangedEvent
import ru.citeck.ecos.data.sql.records.listener.DbRecordCreatedEvent
import ru.citeck.ecos.data.sql.records.listener.DbRecordsListenerAdapter
import ru.citeck.ecos.data.sql.records.perms.DbPermsComponent
import ru.citeck.ecos.data.sql.records.perms.DbRecordPerms
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityConstants
import ru.citeck.ecos.model.domain.perms.dto.PermissionSettingsDto
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.dao.RecordsDao
import ru.citeck.ecos.records3.record.dao.delete.DelStatus
import ru.citeck.ecos.records3.record.dao.impl.proxy.RecordsDaoProxy
import ru.citeck.ecos.webapp.api.entity.toEntityRef

@Configuration
class PermissionSettingsConfig {

    companion object {

        const val PERMISSION_SETTINGS_REPO_ID = "${PermissionSettingsDto.SOURCE_ID}-repo"
        private val PERMISSION_SETTINGS_TABLE = "ecos_" + PermissionSettingsDto.SOURCE_ID.replace('-', '_')

        const val PERMISSION_DEF_ID = "permission-def"
        const val PERMISSION_DEF_TYPE_ID = "permission-def"
        private val PERMISSION_DEF_TABLE = "ecos_" + PERMISSION_DEF_ID.replace('-', '_')
    }

    @Bean
    fun customPermDefDao(recordsService: RecordsService, dbDomainFactory: DbDomainFactory): RecordsDao {

        val typeRef = ModelUtils.getTypeRef(PERMISSION_DEF_TYPE_ID)
        val recordsDao = dbDomainFactory.create(
            DbDomainConfig.create()
                .withRecordsDao(
                    DbRecordsDaoConfig.create {
                        withId(PERMISSION_DEF_ID)
                        withTypeRef(typeRef)
                    }
                )
                .withDataService(
                    DbDataServiceConfig.create {
                        withTable(PERMISSION_DEF_TABLE)
                        withStoreTableMeta(true)
                    }
                )
                .build()
        ).withSchema(AuthorityConstants.DEFAULT_SCHEMA)
            .withPermsComponent(AdminPerms())
            .build()

        recordsDao.addListener(PermissionsRestrictionListener())
        return recordsDao
    }

    @Bean
    fun customPermsDao(
        recordsService: RecordsService,
        dbDomainFactory: DbDomainFactory,
        customPermsService: PermissionSettingsService
    ): RecordsDao {

        return object : RecordsDaoProxy(PermissionSettingsDto.SOURCE_ID, PERMISSION_SETTINGS_REPO_ID) {
            override fun mutate(records: List<LocalRecordAtts>): List<String> {
                for (record in records) {
                    var id = record.id.ifBlank { record.getAtt(PermissionSettingsDto.ATT_ID).asText() }
                    val recordRef = record.getAtt(PermissionSettingsDto.ATT_RECORD_REF).asText().toEntityRef()
                    if (id.isBlank() && recordRef.isEmpty()) {
                        error("'id' or 'recordRef' should be defined")
                    }
                    var existingPerms: PermissionSettingsDto? = null
                    if (id.isBlank()) {
                        existingPerms = customPermsService.getSettingsForRecord(recordRef)
                        if (existingPerms != null) {
                            id = existingPerms.id
                        }
                    }
                    if (id.isNotBlank() && recordRef.isNotEmpty()) {
                        existingPerms = customPermsService.getSettingsForRecord(recordRef)
                        if (existingPerms != null && existingPerms.recordRef != recordRef) {
                            error(
                                "Permissions with id '$id' already " +
                                    "registered for record '${existingPerms.recordRef}' " +
                                    "and can't be applied to '$recordRef'"
                            )
                        }
                    }
                    val newVersion = record.getAtt("version").asInt(0)
                    if (existingPerms != null && newVersion <= existingPerms.version) {
                        record.setAtt(PermissionSettingsDto.ATT_SETTINGS, existingPerms.settings)
                    }
                }
                return super.mutate(records)
            }

            override fun delete(recordIds: List<String>): List<DelStatus> {
                if (AuthContext.isNotRunAsSystemOrAdmin()) {
                    error("Permission denied")
                }
                return super.delete(recordIds)
            }
        }
    }

    @Bean
    fun customPermsRepoDao(recordsService: RecordsService, dbDomainFactory: DbDomainFactory): RecordsDao {

        val typeRef = ModelUtils.getTypeRef(PermissionSettingsDto.TYPE_ID)
        val recordsDao = dbDomainFactory.create(
            DbDomainConfig.create()
                .withRecordsDao(
                    DbRecordsDaoConfig.create {
                        withId(PERMISSION_SETTINGS_REPO_ID)
                        withTypeRef(typeRef)
                    }
                )
                .withDataService(
                    DbDataServiceConfig.create {
                        withTable(PERMISSION_SETTINGS_TABLE)
                        withStoreTableMeta(true)
                    }
                )
                .build()
        ).withSchema(AuthorityConstants.DEFAULT_SCHEMA)
            .withPermsComponent(AdminPerms())
            .build()

        recordsDao.addListener(PermissionsRestrictionListener())
        return recordsDao
    }

    private class PermissionsRestrictionListener : DbRecordsListenerAdapter() {
        override fun onCreated(event: DbRecordCreatedEvent) {
            if (AuthContext.isNotRunAsSystemOrAdmin()) {
                error("Permission denied")
            }
        }
        override fun onChanged(event: DbRecordChangedEvent) {
            if (AuthContext.isNotRunAsSystemOrAdmin()) {
                error("Permission denied")
            }
        }
    }

    private class AdminPerms : DbPermsComponent {

        override fun getRecordPerms(user: String, authorities: Set<String>, record: Any): DbRecordPerms {

            val isAdmin = authorities.contains(AuthRole.ADMIN)

            return object : DbRecordPerms {
                override fun hasAttReadPerms(name: String): Boolean {
                    return isAdmin
                }
                override fun hasAttWritePerms(name: String): Boolean {
                    return isAdmin
                }
                override fun hasReadPerms(): Boolean {
                    return isAdmin
                }
                override fun hasWritePerms(): Boolean {
                    return isAdmin
                }
                override fun getAdditionalPerms(): Set<String> {
                    return emptySet()
                }
                override fun getAuthoritiesWithReadPermission(): Set<String> {
                    return emptySet()
                }
            }
        }
    }
}
