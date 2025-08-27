package ru.citeck.ecos.model.domain.authorities.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.records.listener.*
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.model.domain.authorities.api.records.AuthorityGroupMixin
import ru.citeck.ecos.model.domain.authorities.api.records.AuthorityMixin
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityConstants
import ru.citeck.ecos.model.domain.authorities.service.AuthorityService
import ru.citeck.ecos.model.domain.authorities.service.PrivateGroupsService
import ru.citeck.ecos.model.domain.authsync.service.AuthoritiesSyncService
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.dao.RecordsDao
import ru.citeck.ecos.webapp.api.EcosWebAppApi
import ru.citeck.ecos.webapp.api.authority.EcosAuthoritiesApi
import javax.sql.DataSource

@Configuration
class GroupsConfiguration(
    private val dbDomainFactory: DbDomainFactory,
    private val authoritiesSyncService: AuthoritiesSyncService,
    private val authorityService: AuthorityService,
    private val recordsService: RecordsService,
    private val privateGroupsService: PrivateGroupsService,
    private val authoritiesApi: EcosAuthoritiesApi,
    private val webAppApi: EcosWebAppApi
) {

    @Bean
    fun groupDao(): RecordsDao {
        return GroupsPersonsRecordsDao(
            AuthorityType.GROUP.sourceId,
            AuthorityType.GROUP,
            authoritiesSyncService,
            authorityService,
            privateGroupsService,
            authoritiesApi,
            webAppApi
        )
    }

    @Bean
    fun groupRepo(
        dataSource: DataSource
    ): RecordsDao {

        val permsComponent = GroupDbPermsComponent(recordsService, authorityService)

        val typeRef = ModelUtils.getTypeRef("authority-group")
        val recordsDao = dbDomainFactory.create(
            DbDomainConfig.create()
                .withRecordsDao(
                    DbRecordsDaoConfig.create {
                        withId("authority-group-repo")
                        withTypeRef(typeRef)
                    }
                )
                .withDataService(
                    DbDataServiceConfig.create {
                        withTable("ecos_authority_group")
                        withStoreTableMeta(true)
                    }
                )
                .build()
        ).withSchema(AuthorityConstants.DEFAULT_SCHEMA)
            .withPermsComponent(permsComponent)
            .build()

        val getRecId = { rec: Any ->
            recordsService.getAtt(rec, ScalarType.LOCAL_ID_SCHEMA).asText()
        }

        recordsDao.addListener(object : DbRecordsListenerAdapter() {
            override fun onChanged(event: DbRecordChangedEvent) {
                authorityService.resetGroupCache(getRecId(event.record))
            }
            override fun onCreated(event: DbRecordCreatedEvent) {
                authorityService.resetGroupCache(getRecId(event.record))
            }
            override fun onDeleted(event: DbRecordDeletedEvent) {
                authorityService.resetGroupCache(getRecId(event.record))
            }
        })
        recordsDao.addListener(
            AuthorityGroupsManagementCheckListener(
                recordsService,
                authorityService,
                permsComponent,
                AuthorityType.GROUP
            )
        )

        recordsDao.addAttributesMixin(AuthorityMixin(recordsService, authorityService, AuthorityType.GROUP))
        recordsDao.addAttributesMixin(AuthorityGroupMixin())

        return recordsDao
    }
}
