package ru.citeck.ecos.model.domain.authorities.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.dto.DbTableRef
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.records.listener.*
import ru.citeck.ecos.data.sql.records.perms.DbPermsComponent
import ru.citeck.ecos.data.sql.records.perms.DbRecordPerms
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityConstants
import ru.citeck.ecos.model.domain.authorities.api.records.AuthorityMixin
import ru.citeck.ecos.model.domain.authorities.service.AuthorityService
import ru.citeck.ecos.model.domain.authsync.service.AuthoritiesSyncService
import ru.citeck.ecos.model.domain.authsync.service.AuthorityType
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.RecordsDao
import javax.sql.DataSource

@Configuration
class GroupsConfiguration(
    private val dbDomainFactory: DbDomainFactory,
    private val authoritiesSyncService: AuthoritiesSyncService,
    private val authorityService: AuthorityService,
    private val recordsService: RecordsService
) {

    @Bean
    fun groupDao(): RecordsDao {
        return GroupsPersonsRecordsDao(
            AuthorityType.GROUP.sourceId,
            AuthorityType.GROUP,
            authoritiesSyncService,
            authorityService
        )
    }

    @Bean
    fun groupRepo(dataSource: DataSource): RecordsDao {

        val accessPerms = object : DbRecordPerms {
            override fun getAuthoritiesWithReadPermission(): Set<String> {
                return setOf("EVERYONE")
            }
            override fun isCurrentUserHasWritePerms(): Boolean {
                return AuthContext.getCurrentAuthorities().contains(AuthRole.ADMIN)
            }
        }
        val permsComponent = object : DbPermsComponent {
            override fun getRecordPerms(recordRef: RecordRef): DbRecordPerms {
                return accessPerms
            }
        }

        val typeRef = TypeUtils.getTypeRef("authority-group")
        val recordsDao = dbDomainFactory.create(
            DbDomainConfig.create()
                .withRecordsDao(DbRecordsDaoConfig.create {
                    withId("authority-group-repo")
                    withTypeRef(typeRef)
                })
                .withDataService(DbDataServiceConfig.create {
                    withAuthEnabled(false)
                    withTableRef(DbTableRef(AuthorityConstants.DEFAULT_SCHEMA, "ecos_authority_group"))
                    withTransactional(true)
                    withStoreTableMeta(true)
                })
                .build()
        ).withPermsComponent(permsComponent).build()

        val getRecId = { rec: Any ->
            recordsService.getAtt(rec, "?localId").asText()
        }

        recordsDao.addListener(object : DbRecordsListener {
            override fun onChanged(event: DbRecordChangedEvent) {
                authorityService.resetGroupCache(getRecId(event.record))
            }
            override fun onCreated(event: DbRecordCreatedEvent) {
                authorityService.resetGroupCache(getRecId(event.record))
            }

            override fun onDeleted(event: DbRecordDeletedEvent) {
                authorityService.resetGroupCache(getRecId(event.record))
            }
            override fun onDraftStatusChanged(event: DbRecordDraftStatusChangedEvent) {
            }
            override fun onStatusChanged(event: DbRecordStatusChangedEvent) {
            }
        })

        recordsDao.addAttributesMixin(AuthorityMixin(authorityService, AuthorityType.GROUP))

        return recordsDao
    }
}
