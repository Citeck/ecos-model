package ru.citeck.ecos.model.domain.authsync.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.context.lib.auth.AuthGroup
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
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

        val permsComponent = object : DbPermsComponent {
            override fun getRecordPerms(user: String, authorities: Set<String>, record: Any): DbRecordPerms {
                val isAdmin = authorities.contains(AuthRole.ADMIN)
                return object : DbRecordPerms {
                    override fun getAdditionalPerms(): Set<String> {
                        return emptySet()
                    }
                    override fun getAuthoritiesWithReadPermission(): Set<String> {
                        return setOf(AuthGroup.EVERYONE)
                    }
                    override fun hasAttReadPerms(name: String): Boolean {
                        return true
                    }
                    override fun hasAttWritePerms(name: String): Boolean {
                        return isAdmin
                    }
                    override fun hasReadPerms(): Boolean {
                        return true
                    }
                    override fun hasWritePerms(): Boolean {
                        return isAdmin
                    }
                }
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
                        withTable("ecos_authorities_sync")
                        withStoreTableMeta(true)
                    }
                )
                .build()
        ).withSchema(AuthorityConstants.DEFAULT_SCHEMA).withPermsComponent(permsComponent).build()

        dao.addListener(object : DbRecordsListenerAdapter() {
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
