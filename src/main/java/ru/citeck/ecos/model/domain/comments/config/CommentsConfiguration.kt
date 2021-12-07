package ru.citeck.ecos.model.domain.comments.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.dto.DbTableRef
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.records.perms.DbPermsComponent
import ru.citeck.ecos.data.sql.records.perms.DbRecordPerms
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.record.dao.RecordsDao
import ru.citeck.ecos.records3.record.dao.impl.proxy.RecordsDaoProxy
import javax.sql.DataSource

@Configuration
class CommentsConfiguration(private val dbDomainFactory: DbDomainFactory) {

    @Bean
    fun commentDao(): RecordsDao {
        return RecordsDaoProxy("comment", "comment-repo", null)
    }

    @Bean
    fun commentsRepo(dataSource: DataSource): RecordsDao {

        val fullAccessPerms = object : DbRecordPerms {
            override fun getAuthoritiesWithReadPermission(): Set<String> {
                return setOf("EVERYONE")
            }

            override fun isCurrentUserHasWritePerms(): Boolean {
                return true
            }
        }
        val permsComponent = object : DbPermsComponent {
            override fun getRecordPerms(recordRef: RecordRef): DbRecordPerms {
                return fullAccessPerms
            }
        }

        val typeRef = TypeUtils.getTypeRef("ecos-comment")
        return dbDomainFactory.create(
            DbDomainConfig.create()
                .withRecordsDao(DbRecordsDaoConfig.create {
                    withId("comment-repo")
                    withTypeRef(typeRef)
                })
                .withDataService(DbDataServiceConfig.create {
                    // comments should be visible for all, but editable only for concrete persons
                    withAuthEnabled(false)
                    withTableRef(DbTableRef("public", "ecos_comments"))
                    withTransactional(true)
                    withStoreTableMeta(true)
                })
                .build()
        ).withPermsComponent(permsComponent).build()
    }
}
