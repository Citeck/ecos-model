package ru.citeck.ecos.model.domain.aspects.config

import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.context.lib.auth.AuthGroup
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.records.DbRecordsDao
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.records.listener.DbRecordChangedEvent
import ru.citeck.ecos.data.sql.records.listener.DbRecordCreatedEvent
import ru.citeck.ecos.data.sql.records.listener.DbRecordDeletedEvent
import ru.citeck.ecos.data.sql.records.listener.DbRecordsListenerAdapter
import ru.citeck.ecos.data.sql.records.perms.DbPermsComponent
import ru.citeck.ecos.data.sql.records.perms.DbRecordPerms
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.model.domain.aspects.eapp.AspectArtifactHandler
import ru.citeck.ecos.model.domain.aspects.service.AspectsRegistryInitializer
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.lib.model.aspect.dto.AspectDef
import javax.annotation.PostConstruct

@Configuration
class AspectsConfiguration(
    private val dbDomainFactory: DbDomainFactory,
    private val recordsService: RecordsService,
    private val aspectArtifactHandler: AspectArtifactHandler
) {

    companion object {

        const val ASPECTS_DAO_ID = "aspect"
        const val ASPECTS_REPO_DAO_ID = "aspects-repo"

        val ASPECT_TYPE_ID = "aspect"
        val ASPECT_TYPE_REF = ModelUtils.getTypeRef(ASPECT_TYPE_ID)
    }

    private var aspectsConfig: AspectsRegistryInitializer? = null

    internal fun register(aspectsConfig: AspectsRegistryInitializer) {
        this.aspectsConfig = aspectsConfig
    }

    @PostConstruct
    fun init() {

        val aspectsRepo = createAspectsRepo()

        recordsService.register(aspectsRepo)
        recordsService.register(AspectsRecordsDao(ASPECTS_DAO_ID, ASPECTS_REPO_DAO_ID, null))

        aspectsRepo.addListener(object : DbRecordsListenerAdapter() {
            override fun onCreated(event: DbRecordCreatedEvent) {
                val aspectDef = recordsService.getAtts(event.record, AspectDef::class.java)
                aspectArtifactHandler.aspectWasChanged(aspectDef)
                aspectsConfig?.updateAspect(aspectDef)
            }

            override fun onChanged(event: DbRecordChangedEvent) {
                val aspectDef = recordsService.getAtts(event.record, AspectDef::class.java)
                aspectArtifactHandler.aspectWasChanged(aspectDef)
                aspectsConfig?.updateAspect(aspectDef)
            }

            override fun onDeleted(event: DbRecordDeletedEvent) {
                val aspectId = recordsService.getAtt(event.record, "id").asText()
                if (aspectId.isNotBlank()) {
                    aspectsConfig?.removeAspect(aspectId)
                }
            }
        })
    }

    private fun createAspectsRepo(): DbRecordsDao {

        val permsComponent = object : DbPermsComponent {
            override fun getRecordPerms(user: String, authorities: Set<String>, record: Any): DbRecordPerms {
                return AspectPerms(authorities.contains(AuthRole.ADMIN))
            }
        }

        val dao = dbDomainFactory.create(
            DbDomainConfig.create()
                .withRecordsDao(
                    DbRecordsDaoConfig.create {
                        withId(ASPECTS_REPO_DAO_ID)
                        withTypeRef(ASPECT_TYPE_REF)
                    }
                )
                .withDataService(
                    DbDataServiceConfig.create {
                        withTable("ecos_aspects")
                        withStoreTableMeta(true)
                    }
                )
                .build()
        ).withSchema("public").withPermsComponent(permsComponent).build()

        return dao
    }

    private class AspectPerms(val isAdmin: Boolean) : DbRecordPerms {
        override fun getAllowedPermissions(): Set<String> {
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

        override fun isAllowed(permission: String): Boolean {
            return false
        }
    }
}
