package ru.citeck.ecos.model.domain.aspects.config

import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthGroup
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.dto.DbTableRef
import ru.citeck.ecos.data.sql.records.DbRecordsDao
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.records.listener.DbRecordChangedEvent
import ru.citeck.ecos.data.sql.records.listener.DbRecordCreatedEvent
import ru.citeck.ecos.data.sql.records.listener.DbRecordsListenerAdapter
import ru.citeck.ecos.data.sql.records.perms.DbPermsComponent
import ru.citeck.ecos.data.sql.records.perms.DbRecordPerms
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.model.domain.aspects.eapp.AspectArtifactHandler
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.api.entity.EntityRef
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

    @PostConstruct
    fun init() {

        val aspectsRepo = createAspectsRepo()

        recordsService.register(aspectsRepo)
        recordsService.register(AspectsRecordsDao(ASPECTS_DAO_ID, ASPECTS_REPO_DAO_ID, null))

        aspectsRepo.addListener(object : DbRecordsListenerAdapter() {
            override fun onCreated(event: DbRecordCreatedEvent) {
                val aspectDef = recordsService.getAtts(event.record, AspectDef::class.java)
                aspectArtifactHandler.aspectWasChanged(aspectDef)
            }
            override fun onChanged(event: DbRecordChangedEvent) {
                val aspectDef = recordsService.getAtts(event.record, AspectDef::class.java)
                aspectArtifactHandler.aspectWasChanged(aspectDef)
            }
        })
    }

    private fun createAspectsRepo(): DbRecordsDao {

        val fullAccessPerms = object : DbRecordPerms {
            override fun getAuthoritiesWithReadPermission(): Set<String> {
                return setOf(AuthGroup.EVERYONE)
            }
            override fun isCurrentUserHasWritePerms(): Boolean {
                return AuthContext.isRunAsAdmin()
            }
            override fun isCurrentUserHasAttReadPerms(name: String): Boolean {
                return true
            }
            override fun isCurrentUserHasAttWritePerms(name: String): Boolean {
                return true
            }
        }
        val permsComponent = object : DbPermsComponent {
            override fun getRecordPerms(recordRef: EntityRef): DbRecordPerms {
                return fullAccessPerms
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
                        withAuthEnabled(true)
                        withTableRef(DbTableRef("public", "ecos_aspects"))
                        withTransactional(true)
                        withStoreTableMeta(true)
                    }
                )
                .build()
        ).withPermsComponent(permsComponent).build()

        return dao
    }
}
