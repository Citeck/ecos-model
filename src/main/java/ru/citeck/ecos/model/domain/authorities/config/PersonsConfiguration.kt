package ru.citeck.ecos.model.domain.authorities.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthGroup
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.context.lib.auth.AuthUser
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.records.DbRecordsControlAtts
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.records.listener.DbRecordChangedEvent
import ru.citeck.ecos.data.sql.records.listener.DbRecordCreatedEvent
import ru.citeck.ecos.data.sql.records.listener.DbRecordDeletedEvent
import ru.citeck.ecos.data.sql.records.listener.DbRecordsListenerAdapter
import ru.citeck.ecos.data.sql.records.perms.DbPermsComponent
import ru.citeck.ecos.data.sql.records.perms.DbRecordPerms
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.model.domain.authorities.api.records.AuthorityMixin
import ru.citeck.ecos.model.domain.authorities.api.records.PersonMixin
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityConstants
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityGroupConstants
import ru.citeck.ecos.model.domain.authorities.constant.PersonConstants
import ru.citeck.ecos.model.domain.authorities.service.AuthorityService
import ru.citeck.ecos.model.domain.authorities.service.ExtUsersService
import ru.citeck.ecos.model.domain.authorities.service.PersonEventsService
import ru.citeck.ecos.model.domain.authorities.service.PrivateGroupsService
import ru.citeck.ecos.model.domain.authsync.service.AuthoritiesSyncService
import ru.citeck.ecos.model.domain.workspace.service.EmodelWorkspaceService
import ru.citeck.ecos.model.domain.workspace.utils.WorkspaceSystemIdUtils
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.model.lib.permissions.service.RecordPermsService
import ru.citeck.ecos.model.lib.role.service.RoleService
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.model.service.keycloak.KeycloakUserService
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.RecordsServiceFactory
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.RecordsDao
import ru.citeck.ecos.records3.record.dao.impl.proxy.MutateProxyProcessor
import ru.citeck.ecos.records3.record.dao.impl.proxy.ProxyProcContext
import ru.citeck.ecos.txn.lib.TxnContext
import ru.citeck.ecos.webapp.api.EcosWebAppApi
import ru.citeck.ecos.webapp.api.authority.EcosAuthoritiesApi
import ru.citeck.ecos.webapp.api.entity.EntityRef
import javax.sql.DataSource
import kotlin.reflect.jvm.jvmName

@Configuration
class PersonsConfiguration(
    private val eventsService: PersonEventsService,
    private val recordsService: RecordsService,
    private val permsService: RecordPermsService,
    private var roleService: RoleService,
    private val authorityService: AuthorityService,
    private val dbDomainFactory: DbDomainFactory,
    private val authoritiesSyncService: AuthoritiesSyncService,
    private val keycloakUserService: KeycloakUserService,
    private val privateGroupsService: PrivateGroupsService,
    private val authoritiesApi: EcosAuthoritiesApi,
    private val workspaceService: EmodelWorkspaceService,
    private val ecosWebAppApi: EcosWebAppApi
) {

    companion object {
        const val USERS_PROFILE_ADMIN_WITH_PREFIX = AuthGroup.PREFIX + AuthorityGroupConstants.USERS_PROFILE_ADMIN_GROUP

        // users with this group can edit authorityGroups
        const val GROUPS_MANAGERS_GROUP_WITH_PREFIX = AuthGroup.PREFIX + AuthorityGroupConstants.GROUPS_MANAGERS_GROUP

        private val UPDATE_KK_USERS_TXN_KEY = PersonsConfiguration::class.jvmName + "-update-kk-users"
        private val DELETE_KK_USERS_TXN_KEY = PersonsConfiguration::class.jvmName + "-delete-kk-users"
    }

    @Bean
    fun personDao(): RecordsDao {
        val recordsDao = object : GroupsPersonsRecordsDao(
            "person",
            AuthorityType.PERSON,
            authoritiesSyncService,
            authorityService,
            privateGroupsService,
            authoritiesApi,
            workspaceService,
            ecosWebAppApi,
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
                override fun mutatePostProcess(records: List<EntityRef>, context: ProxyProcContext): List<EntityRef> {
                    return records
                }
            }
        ) {
            override fun setRecordsServiceFactory(serviceFactory: RecordsServiceFactory) {
                serviceFactory.recordsResolver.registerVirtualRecord(
                    EntityRef.create(AuthorityType.PERSON.sourceId, AuthUser.SYSTEM),
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
        dataSource: DataSource,
        extUsersService: ExtUsersService
    ): RecordsDao {

        val typeRef = ModelUtils.getTypeRef("person")
        val permsComponent = object : DbPermsComponent {

            override fun getRecordPerms(user: String, authorities: Set<String>, record: Any): DbRecordPerms {

                val userName = recordsService.getAtt(record, ScalarType.LOCAL_ID_SCHEMA).asText()

                return object : DbRecordPerms {

                    private val attsPerms by lazyAsSystem {
                        permsService.getRecordAttsPerms(record)
                    }
                    private val roles by lazyAsSystem {
                        roleService.getRolesForAuthorities(record, typeRef, authorities).toSet()
                    }

                    override fun hasAttReadPerms(name: String): Boolean {
                        return true
                    }
                    override fun hasAttWritePerms(name: String): Boolean {
                        if (!hasWritePerms()) {
                            return false
                        }
                        val localAttPerms = attsPerms
                        if (!isSystem() && localAttPerms != null) {
                            val perms = localAttPerms.getPermissions(name)
                            return perms.isWriteAllowed(roles)
                        } else {
                            return true
                        }
                    }
                    override fun hasReadPerms(): Boolean {
                        return true
                    }
                    override fun hasWritePerms(): Boolean {
                        return isSystemOrAdminOrOwner() ||
                            authorities.contains(USERS_PROFILE_ADMIN_WITH_PREFIX) ||
                            authorities.contains(GROUPS_MANAGERS_GROUP_WITH_PREFIX)
                    }

                    override fun getAdditionalPerms(): Set<String> {
                        val perms = HashSet<String>()
                        if (keycloakUserService.isEnabled() && isSystemOrAdminOrOwner()) {
                            perms.add("CHANGE_PASSWORD")
                        }
                        return perms
                    }

                    override fun getAuthoritiesWithReadPermission(): Set<String> {
                        return setOf(AuthGroup.EVERYONE)
                    }

                    private fun isSystemOrAdminOrOwner(): Boolean {
                        return isSystem() ||
                            authorities.contains(AuthRole.ADMIN) ||
                            userName == user
                    }

                    private fun isSystem(): Boolean {
                        return authorities.contains(AuthRole.SYSTEM)
                    }

                    private fun <T> lazyAsSystem(initializer: () -> T): Lazy<T> {
                        return lazy { AuthContext.runAsSystem(initializer) }
                    }
                }
            }
        }

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

        recordsDao.addAttributesMixin(PersonMixin(recordsService, authorityService, extUsersService))
        recordsDao.addAttributesMixin(AuthorityMixin(recordsService, authorityService, AuthorityType.PERSON))

        val getRecLocalId = { rec: Any ->
            recordsService.getAtt(rec, ScalarType.LOCAL_ID_SCHEMA).asText()
        }

        recordsDao.addListener(
            AuthorityGroupsManagementCheckListener(
                recordsService,
                authorityService,
                GroupDbPermsComponent(recordsService, authorityService),
                AuthorityType.PERSON
            )
        )
        recordsDao.addListener(object : DbRecordsListenerAdapter() {
            override fun onChanged(event: DbRecordChangedEvent) {
                val userName = getRecLocalId(event.record)
                authorityService.resetPersonCache(userName)
                eventsService.onPersonChanged(event)
                if (keycloakUserService.isEnabled()) {
                    TxnContext.processSetBeforeCommit(UPDATE_KK_USERS_TXN_KEY, userName) { users ->
                        users.forEach { keycloakUserService.updateUser(it) }
                    }
                }
            }
            override fun onCreated(event: DbRecordCreatedEvent) {
                val userName = getRecLocalId(event.record)
                val userWsSysId = WorkspaceSystemIdUtils.createId(userName)
                if (userWsSysId != userName) {
                    AuthContext.runAsSystem {
                        recordsService.mutate(
                            event.localRef,
                            mapOf(
                                PersonConstants.ATT_WS_SYS_ID to userWsSysId,
                                DbRecordsControlAtts.DISABLE_EVENTS to true,
                                DbRecordsControlAtts.DISABLE_AUDIT to true
                            )
                        )
                    }
                }
                authorityService.resetPersonCache(userName)
                eventsService.onPersonCreated(event)
                if (keycloakUserService.isEnabled()) {
                    TxnContext.processSetBeforeCommit(UPDATE_KK_USERS_TXN_KEY, userName) { users ->
                        users.forEach { keycloakUserService.updateUser(it) }
                    }
                }
            }
            override fun onDeleted(event: DbRecordDeletedEvent) {
                val userName = getRecLocalId(event.record)
                authorityService.resetPersonCache(userName)
                if (keycloakUserService.isEnabled()) {
                    TxnContext.processSetBeforeCommit(DELETE_KK_USERS_TXN_KEY, userName) { users ->
                        users.forEach { keycloakUserService.deleteUser(it) }
                    }
                }
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

        fun getFirstName(): MLText {
            return name
        }
    }

    private class PersonAwayStateAtts(
        @AttName(PersonConstants.ATT_AT_WORKPLACE + "?bool!")
        val atWorkplace: Boolean
    )
}
