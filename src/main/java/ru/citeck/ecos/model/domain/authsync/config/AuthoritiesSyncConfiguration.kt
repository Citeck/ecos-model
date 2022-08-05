package ru.citeck.ecos.model.domain.authsync.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthGroup
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.dto.DbTableRef
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.records.listener.*
import ru.citeck.ecos.data.sql.records.perms.DbPermsComponent
import ru.citeck.ecos.data.sql.records.perms.DbRecordPerms
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.events2.type.RecordEventsService
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityConstants
import ru.citeck.ecos.model.domain.authsync.eapp.AuthoritiesSyncArtifactHandler
import ru.citeck.ecos.model.domain.authsync.service.AuthoritiesSyncDef
import ru.citeck.ecos.model.domain.authsync.service.AuthoritiesSyncService
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.RecordsDao
import ru.citeck.ecos.records3.record.dao.impl.proxy.RecordsDaoProxy
import javax.sql.DataSource

@Configuration
class AuthoritiesSyncConfiguration(
    private val recordsService: RecordsService,
    private val dbDomainFactory: DbDomainFactory,
    private val authoritiesSyncService: AuthoritiesSyncService,
    private val recordEventsService: RecordEventsService,
    private val authoritiesSyncArtifactHandler: AuthoritiesSyncArtifactHandler
) {

    @Bean
    fun authoritiesSyncDao(): RecordsDao {
        return RecordsDaoProxy(
            AuthoritiesSyncService.SOURCE_ID,
            "${AuthoritiesSyncService.SOURCE_ID}-repo",
            null
        )
    }

    @Bean
    fun authoritiesSyncRepo(dataSource: DataSource): RecordsDao {

        val adminAccessPerms = object : DbRecordPerms {
            override fun getAuthoritiesWithReadPermission(): Set<String> {
                return setOf(AuthGroup.EVERYONE)
            }
            override fun isCurrentUserHasWritePerms(): Boolean {
                return AuthContext.getCurrentRunAsAuthorities().contains(AuthRole.ADMIN)
            }
            override fun isCurrentUserHasAttReadPerms(name: String): Boolean {
                return true
            }
            override fun isCurrentUserHasAttWritePerms(name: String): Boolean {
                return isCurrentUserHasWritePerms()
            }
        }
        val permsComponent = object : DbPermsComponent {
            override fun getRecordPerms(recordRef: RecordRef): DbRecordPerms {
                return adminAccessPerms
            }
        }

        val dao = dbDomainFactory.create(
            DbDomainConfig.create()
                .withRecordsDao(
                    DbRecordsDaoConfig.create {
                        withId("${AuthoritiesSyncService.SOURCE_ID}-repo")
                        withTypeRef(AuthoritiesSyncService.TYPE_REF)
                    }
                )
                .withDataService(
                    DbDataServiceConfig.create {
                        withAuthEnabled(true)
                        withTableRef(DbTableRef(AuthorityConstants.DEFAULT_SCHEMA, "ecos_authorities_sync"))
                        withStoreTableMeta(true)
                    }
                )
                .build()
        ).withPermsComponent(permsComponent).build()

        dao.addListener(object : DbRecordsListener {
            override fun onChanged(event: DbRecordChangedEvent) {
                recordWasUpdated(event.record)
                recordEventsService.emitRecChanged(event.record, event.before, event.after)
            }
            override fun onCreated(event: DbRecordCreatedEvent) {
                recordWasUpdated(event.record)
                recordEventsService.emitRecCreated(event.record)
            }
            override fun onDeleted(event: DbRecordDeletedEvent) {
                authoritiesSyncService.updateSynchronizations()
            }
            override fun onDraftStatusChanged(event: DbRecordDraftStatusChangedEvent) {}
            override fun onStatusChanged(event: DbRecordStatusChangedEvent) {}
        })

        return dao
    }

    private fun recordWasUpdated(record: Any) {
        val def = recordsService.getAtts(record, AuthoritiesSyncDef::class.java)
        if (def.id.isEmpty()) {
            error("id is not valid. Rec: $record")
        }
        authoritiesSyncArtifactHandler.syncWasChanged(def)
        authoritiesSyncService.updateSynchronizations()
    }
}
