package ru.citeck.ecos.model.domain.aspects.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.context.lib.auth.AuthGroup
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.dto.DbTableRef
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.records.perms.DbPermsComponent
import ru.citeck.ecos.data.sql.records.perms.DbRecordPerms
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.records3.record.dao.RecordsDao
import ru.citeck.ecos.records3.record.dao.impl.proxy.RecordsDaoProxy
import ru.citeck.ecos.webapp.api.entity.EntityRef
import javax.sql.DataSource

const val ASPECT_REPO_DAO_ID = "aspect-repo"
val ECOS_ASPECT_TYPE_REF = TypeUtils.getTypeRef("ecos-aspect")

@Configuration
class AspectsConfiguration(private val dbDomainFactory: DbDomainFactory) {

    @Bean
    fun aspectDao(): RecordsDao {
        return RecordsDaoProxy("aspect", ASPECT_REPO_DAO_ID, null)
    }

    @Bean
    fun aspectsRepo(dataSource: DataSource): RecordsDao {

        val fullAccessPerms = object : DbRecordPerms {
            override fun getAuthoritiesWithReadPermission(): Set<String> {
                return setOf(AuthGroup.EVERYONE)
            }

            override fun isCurrentUserHasWritePerms(): Boolean {
                return true
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
                        withId(ASPECT_REPO_DAO_ID)
                        withTypeRef(ECOS_ASPECT_TYPE_REF)
                    }
                )
                .withDataService(
                    DbDataServiceConfig.create {
                        withAuthEnabled(false)
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
