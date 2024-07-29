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
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.RecordsDao
import ru.citeck.ecos.records3.record.dao.impl.proxy.RecordsDaoProxy
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
    fun activityDao(): RecordsDao {
        return RecordsDaoProxy(ACTIVITY_DAO_ID, ACTIVITY_REPO_DAO_ID)
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
                        if (name == ActivityDesc.ATT_RECORD) {
                            return true
                        }
                        return hasReadPerms()
                    }

                    override fun hasAttWritePerms(name: String): Boolean {
                        if (name == ActivityDesc.ATT_RECORD) {
                            return isAdmin
                        }
                        return hasWritePerms()
                    }

                    override fun hasReadPerms(): Boolean {
                        if (isAdmin) {
                            return true
                        }
                        return AuthContext.runAs(user, authorities.toList()) {
                            recordsService.getAtt(
                                record,
                                "${ActivityDesc.ATT_RECORD}.permissions._has.Read?bool!"
                            ).asBoolean()
                        }
                    }

                    override fun hasWritePerms(): Boolean {
                        val activityData = AuthContext.runAsSystem {
                            recordsService.getAtts(record, ActivityData::class.java)
                        }
                        return isAdmin || activityData.creator == user
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
        ).withSchema("public")
            .withPermsComponent(permsComponent)
            .build()

        return dao
    }

    data class ActivityData(
        @AttName("_creator.id")
        val creator: String
    )
}

