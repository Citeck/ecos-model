package ru.citeck.ecos.model.domain.authorities.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.context.lib.auth.*
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.records.listener.*
import ru.citeck.ecos.data.sql.records.perms.DbPermsComponent
import ru.citeck.ecos.data.sql.records.perms.DbRecordPerms
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.model.domain.authorities.api.records.AuthorityMixin
import ru.citeck.ecos.model.domain.authorities.api.records.PersonMixin
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityConstants
import ru.citeck.ecos.model.domain.authorities.service.AuthorityService
import ru.citeck.ecos.model.domain.authorities.service.PersonEventsService
import ru.citeck.ecos.model.domain.authsync.service.AuthoritiesSyncService
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.RecordsServiceFactory
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.dao.RecordsDao
import ru.citeck.ecos.records3.record.dao.impl.proxy.MutateProxyProcessor
import ru.citeck.ecos.records3.record.dao.impl.proxy.ProxyProcContext
import ru.citeck.ecos.webapp.api.entity.EntityRef
import javax.sql.DataSource

@Configuration
class PersonsConfiguration(
    private val eventsService: PersonEventsService,
    private val recordsService: RecordsService,
    private val authorityService: AuthorityService,
    private val dbDomainFactory: DbDomainFactory,
    private val authoritiesSyncService: AuthoritiesSyncService
) {

    @Bean
    fun personDao(): RecordsDao {
        val recordsDao = object : GroupsPersonsRecordsDao(
            "person",
            AuthorityType.PERSON,
            authoritiesSyncService,
            authorityService,
            object : MutateProxyProcessor {

                override fun mutatePreProcess(
                    atts: List<LocalRecordAtts>,
                    context: ProxyProcContext
                ): List<LocalRecordAtts> {
                    return atts.map {
                        val id = it.attributes["id"].asText()
                        if (id != id.lowercase()) {
                            val newAtts = it.attributes.deepCopy()
                            newAtts["id"] = id.lowercase()
                            LocalRecordAtts(it.id, newAtts)
                        } else {
                            it
                        }
                    }
                }
                override fun mutatePostProcess(records: List<RecordRef>, context: ProxyProcContext): List<RecordRef> {
                    return records
                }
            }
        ) {
            override fun setRecordsServiceFactory(serviceFactory: RecordsServiceFactory) {
                serviceFactory.recordsResolver.registerVirtualRecord(
                    RecordRef.create(AuthorityType.PERSON.sourceId, AuthUser.SYSTEM),
                    SystemUserRecord()
                )
                super.setRecordsServiceFactory(serviceFactory)
            }
        }

        return recordsDao
    }

    @Bean
    fun personRepo(dataSource: DataSource): RecordsDao {

        val permsComponent = object : DbPermsComponent {

            override fun getRecordPerms(record: Any): DbRecordPerms {

                val localId = recordsService.getAtt(record, "?localId").asText()

                return object : DbRecordPerms {
                    override fun getAuthoritiesWithReadPermission(): Set<String> {
                        return setOf(AuthGroup.EVERYONE)
                    }
                    override fun isCurrentUserHasWritePerms(): Boolean {
                        val auth = AuthContext.getCurrentFullAuth()
                        return localId == auth.getUser() || auth.getAuthorities().contains(AuthRole.ADMIN)
                    }
                    override fun isCurrentUserHasAttReadPerms(name: String): Boolean {
                        return true
                    }
                    override fun isCurrentUserHasAttWritePerms(name: String): Boolean {
                        return isCurrentUserHasWritePerms()
                    }
                }
            }
        }

        val typeRef = ModelUtils.getTypeRef("person")
        val recordsDao = dbDomainFactory.create(
            DbDomainConfig.create()
                .withRecordsDao(
                    DbRecordsDaoConfig.create {
                        withId("person-repo")
                        withTypeRef(typeRef)
                    }
                )
                .withDataService(
                    DbDataServiceConfig.create {
                        // persons should be visible for all, but editable only for concrete persons
                        withTable("ecos_person")
                        withStoreTableMeta(true)
                    }
                )
                .build()
        ).withSchema(AuthorityConstants.DEFAULT_SCHEMA).withPermsComponent(permsComponent).build()

        recordsDao.addAttributesMixin(PersonMixin(authorityService))
        recordsDao.addAttributesMixin(AuthorityMixin(recordsService, authorityService, AuthorityType.PERSON))

        val getRecId = { rec: Any ->
            recordsService.getAtt(rec, "?localId").asText()
        }

        recordsDao.addListener(object : DbRecordsListenerAdapter() {
            override fun onChanged(event: DbRecordChangedEvent) {
                authorityService.resetPersonCache(getRecId(event.record))
                eventsService.onPersonChanged(event)
            }
            override fun onCreated(event: DbRecordCreatedEvent) {
                authorityService.resetPersonCache(getRecId(event.record))
                eventsService.onPersonCreated(event)
            }
            override fun onDeleted(event: DbRecordDeletedEvent) {
                authorityService.resetPersonCache(getRecId(event.record))
            }
        })

        return recordsDao
    }

    private class SystemUserRecord {
        val name = MLText(
            I18nContext.ENGLISH to "System",
            I18nContext.RUSSIAN to "Система"
        )
        val userName = AuthUser.SYSTEM
    }
}
