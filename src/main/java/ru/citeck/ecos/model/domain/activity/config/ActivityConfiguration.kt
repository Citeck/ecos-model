package ru.citeck.ecos.model.domain.activity.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthGroup
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.records.perms.DbPermsComponent
import ru.citeck.ecos.data.sql.records.perms.DbRecordPerms
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.RecordsDao
import javax.sql.DataSource

@Configuration
class ActivityConfiguration(private val dbDomainFactory: DbDomainFactory) {

    companion object {
        const val TYPE_ID = "ecos-activity"
        const val ACTIVITY_DAO_ID = "activity"
        const val ACTIVITY_REPO_DAO_ID = "activity-repo"

        val ECOS_ACTIVITY_TYPE_REF = ModelUtils.getTypeRef(TYPE_ID)
    }

    @Bean
    fun activityRepo(
        dataSource: DataSource,
        recordsService: RecordsService
    ): RecordsDao {

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
                        if (name == RecordConstants.ATT_PARENT) {
                            return true
                        }
                        return hasReadPerms()
                    }

                    override fun hasAttWritePerms(name: String): Boolean {
                        return when (name) {
                            RecordConstants.ATT_PARENT -> isAdmin
                            else -> hasWritePerms()
                        }
                    }

                    override fun hasReadPerms(): Boolean {
                        if (isAdmin) {
                            return true
                        }
                        return AuthContext.runAs(user, authorities.toList()) {
                            recordsService.getAtt(
                                record,
                                "_parent.permissions._has.Read?bool!"
                            ).asBoolean()
                        }
                    }

                    override fun hasWritePerms(): Boolean {
                        val activityData = AuthContext.runAsSystem {
                            recordsService.getAtts(record, ActivityAtts::class.java)
                        }
                        return activityData.creator == user || activityData.responsible == user
                    }
                }
            }
        }

        val dao = dbDomainFactory.create(
            DbDomainConfig.create {
                withRecordsDao(
                    DbRecordsDaoConfig.create {
                        withId(ACTIVITY_REPO_DAO_ID)
                        withTypeRef(ECOS_ACTIVITY_TYPE_REF)
                    }
                )
                withDataService(
                    DbDataServiceConfig.create {
                        withTable("ecos_activity")
                        withStoreTableMeta(true)
                    }
                )
            }
        ).withPermsComponent(permsComponent)
            .withSchema("public")
            .build()

        return dao
    }

    class ActivityAtts(
        @AttName("_creator?localId")
        val creator: String,
        @AttName("responsible?localId")
        val responsible: String?
    )
}
