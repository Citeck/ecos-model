package ru.citeck.ecos.model.domain.authorities.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import ru.citeck.ecos.commons.data.ObjectData
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
import ru.citeck.ecos.model.domain.authorities.api.records.PersonMixin
import ru.citeck.ecos.model.domain.authorities.constant.PersonConstants
import ru.citeck.ecos.model.domain.authorities.service.AuthorityService
import ru.citeck.ecos.model.domain.authorities.service.PersonEventsService
import ru.citeck.ecos.model.domain.authsync.service.AuthoritiesSyncService
import ru.citeck.ecos.model.domain.authsync.service.AuthorityType
import ru.citeck.ecos.model.domain.events.emitter.DbRecordsEcosEventsAdapter
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.dao.RecordsDao
import ru.citeck.ecos.records3.record.dao.impl.proxy.MutateProxyProcessor
import ru.citeck.ecos.records3.record.dao.impl.proxy.ProxyProcContext
import javax.sql.DataSource

@Configuration
class PersonsConfiguration(
    private val eventsService: PersonEventsService,
    private val recordsService: RecordsService,
    private val authorityService: AuthorityService,
    private val dbDomainFactory: DbDomainFactory,
    private val authoritiesSyncService: AuthoritiesSyncService,
    private val dbRecordsEcosEventsAdapter: DbRecordsEcosEventsAdapter
) {

    private var initialized = false

    @Bean
    fun personDao(): RecordsDao {
        val recordsDao = GroupsPersonsRecordsDao(
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
                        val id = it.attributes.get("id").asText()
                        if (id != id.lowercase()) {
                            val newAtts = it.attributes.deepCopy()
                            newAtts.set("id", id.lowercase())
                            LocalRecordAtts(it.id, newAtts)
                        } else {
                            it
                        }
                    }
                }
                override fun mutatePostProcess(records: List<RecordRef>, context: ProxyProcContext): List<RecordRef> {
                    return records
                }
            })

        return recordsDao
    }

    @Bean
    fun personRepo(dataSource: DataSource): RecordsDao {

        val permsComponent = object : DbPermsComponent {
            override fun getRecordPerms(recordRef: RecordRef): DbRecordPerms {

                return object : DbRecordPerms {
                    override fun getAuthoritiesWithReadPermission(): Set<String> {
                        return setOf("EVERYONE")
                    }

                    override fun isCurrentUserHasWritePerms(): Boolean {
                        val auth = AuthContext.getCurrentFullAuth()
                        return recordRef.id == auth.getUser() || auth.getAuthorities().contains(AuthRole.ADMIN)
                    }
                }
            }
        }

        val typeRef = TypeUtils.getTypeRef("person")
        val recordsDao = dbDomainFactory.create(
            DbDomainConfig.create()
                .withRecordsDao(DbRecordsDaoConfig.create {
                    withId("person-repo")
                    withTypeRef(typeRef)
                })
                .withDataService(DbDataServiceConfig.create {
                    // persons should be visible for all, but editable only for concrete persons
                    withAuthEnabled(false)
                    withTableRef(DbTableRef(AuthorityConstants.DEFAULT_SCHEMA, "ecos_person"))
                    withTransactional(true)
                    withStoreTableMeta(true)
                })
                .build()
        ).withPermsComponent(permsComponent).build()

        recordsDao.addAttributesMixin(PersonMixin(authorityService))
        recordsDao.addAttributesMixin(AuthorityMixin(authorityService, AuthorityType.PERSON))

        val getRecId = { rec: Any ->
            recordsService.getAtt(rec, "?localId").asText()
        }

        recordsDao.addListener(object : DbRecordsListener {
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
            override fun onDraftStatusChanged(event: DbRecordDraftStatusChangedEvent) {
            }
            override fun onStatusChanged(event: DbRecordStatusChangedEvent) {
            }
        })
        recordsDao.addListener(dbRecordsEcosEventsAdapter)

        return recordsDao
    }

    @EventListener
    fun onServicesInitialized(event: ContextRefreshedEvent) {

        if (initialized) {
            return
        }
        initialized = true

        val isAdminNotExists = recordsService.getAtt(
            RecordRef.create(AuthorityType.PERSON.sourceId, "admin"),
            RecordConstants.ATT_NOT_EXISTS + "?bool"
        ).asBoolean()

        if (isAdminNotExists) {
            val userAtts = ObjectData.create()
            userAtts.set("id", "admin")
            userAtts.set(PersonConstants.ATT_FIRST_NAME, "admin")
            userAtts.set(PersonConstants.ATT_LAST_NAME, "admin")
            userAtts.set(PersonConstants.ATT_EMAIL, "admin@admin.ru")
            recordsService.create(AuthorityType.PERSON.sourceId, userAtts)
        }
    }
}
