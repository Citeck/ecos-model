package ru.citeck.ecos.model.domain.authorities.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
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
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityGroupConstants
import ru.citeck.ecos.model.domain.authorities.constant.PersonConstants
import ru.citeck.ecos.model.domain.authorities.service.AuthorityService
import ru.citeck.ecos.model.domain.authorities.service.PersonEventsService
import ru.citeck.ecos.model.domain.authsync.service.AuthoritiesSyncService
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.RecordsServiceFactory
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
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
    private val authoritiesSyncService: AuthoritiesSyncService
) {

    companion object {
        const val USERS_PROFILE_ADMIN_WITH_PREFIX = AuthGroup.PREFIX + AuthorityGroupConstants.USERS_PROFILE_ADMIN_GROUP

        // users with this group can edit authorityGroups
        const val GROUPS_MANAGERS_GROUP_WITH_PREFIX = AuthGroup.PREFIX + AuthorityGroupConstants.GROUPS_MANAGERS_GROUP
    }

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
                        val newAtts = it.attributes.deepCopy()
                        val recordId = if (it.id == PersonConstants.CURRENT_USER_ID) {
                            AuthContext.getCurrentUser()
                        } else {
                            it.id
                        }
                        preProcessPersonBeforeMutation(recordId.ifBlank { newAtts["id"].asText() }.lowercase(), newAtts)
                        LocalRecordAtts(recordId, newAtts)
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

    private fun preProcessPersonBeforeMutation(id: String, attributes: ObjectData) {

        if (attributes.has("id")) {
            attributes["id"] = attributes["id"].asText().lowercase()
        }
        val personRef = AuthorityType.PERSON.getRef(id)

        if (attributes.has(PersonConstants.ATT_AT_WORKPLACE) &&
            !attributes.has(PersonConstants.ATT_AWAY_AUTH_DELEGATION_ENABLED)
        ) {

            val newState = attributes[PersonConstants.ATT_AT_WORKPLACE]
            if (newState.isBoolean() && !newState.asBoolean()) {
                val currentState = recordsService.getAtts(personRef, PersonAwayStateAtts::class.java)
                if (currentState.atWorkplace) {
                    attributes[PersonConstants.ATT_AWAY_AUTH_DELEGATION_ENABLED] = true
                }
            }
        }
    }

    @Bean
    fun personRepo(
        dataSource: DataSource
    ): RecordsDao {

        val permsComponent = object : DbPermsComponent {

            override fun getRecordPerms(user: String, authorities: Set<String>, record: Any): DbRecordPerms {

                val userName = recordsService.getAtt(record, ScalarType.LOCAL_ID_SCHEMA).asText()

                return object : DbRecordPerms {
                    override fun hasAttReadPerms(name: String): Boolean {
                        return true
                    }
                    override fun hasAttWritePerms(name: String): Boolean {
                        return hasWritePerms()
                    }
                    override fun hasReadPerms(): Boolean {
                        return true
                    }
                    override fun hasWritePerms(): Boolean {
                        return authorities.contains(AuthRole.SYSTEM) ||
                            authorities.contains(AuthRole.ADMIN) ||
                            userName == user ||
                            authorities.contains(USERS_PROFILE_ADMIN_WITH_PREFIX) ||
                            authorities.contains(GROUPS_MANAGERS_GROUP_WITH_PREFIX)
                    }
                    override fun getAuthoritiesWithReadPermission(): Set<String> {
                        return setOf(AuthGroup.EVERYONE)
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

        recordsDao.addAttributesMixin(PersonMixin(recordsService, authorityService))
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
        recordsDao.addListener(
            AuthorityGroupsManagementCheckListener(
                recordsService,
                GroupDbPermsComponent(recordsService, authorityService),
                AuthorityType.PERSON
            )
        )
        return recordsDao
    }

    private class SystemUserRecord {
        val name = MLText(
            I18nContext.ENGLISH to "System",
            I18nContext.RUSSIAN to "Система"
        )
        val userName = AuthUser.SYSTEM
    }

    private class PersonAwayStateAtts(
        @AttName(PersonConstants.ATT_AT_WORKPLACE + "?bool!")
        val atWorkplace: Boolean
    )
}
