package ru.citeck.ecos.model.domain.comments.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.data.sql.datasource.DbDataSourceImpl
import ru.citeck.ecos.data.sql.dto.DbTableRef
import ru.citeck.ecos.data.sql.pg.PgDataServiceFactory
import ru.citeck.ecos.data.sql.records.DbRecordsDao
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.records.perms.DbPermsComponent
import ru.citeck.ecos.data.sql.records.perms.DbRecordPerms
import ru.citeck.ecos.data.sql.repo.entity.DbEntity
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.data.sql.service.DbDataServiceImpl
import ru.citeck.ecos.model.lib.type.repo.TypesRepo
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.record.dao.RecordsDao
import ru.citeck.ecos.records3.record.dao.impl.proxy.RecordsDaoProxy
import javax.sql.DataSource

@Configuration
class CommentsConfiguration(private val typesRepo: TypesRepo) {

    @Bean
    fun commentDao(): RecordsDao {
        return RecordsDaoProxy("comment", "comment-repo", null)
    }

    @Bean
    fun commentsRepo(dataSource: DataSource): RecordsDao {

        val pgDataServiceFactory = PgDataServiceFactory()
        val dbDataSource = DbDataSourceImpl(dataSource)
        val dbDataService = DbDataServiceImpl(
            DbEntity::class.java,
            DbDataServiceConfig.create {
                // comments should be visible for all, but editable only for concrete persons
                withAuthEnabled(false)
                withTableRef(DbTableRef("public", "ecos_comments"))
                withTransactional(true)
                withStoreTableMeta(true)
                withMaxItemsToAllowSchemaMigration(1000)
            },
            dbDataSource,
            pgDataServiceFactory
        )

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

        return DbRecordsDao(
            "comment-repo",
            DbRecordsDaoConfig(
                TypeUtils.getTypeRef("ecos-comment"),
                insertable = true,
                updatable = true,
                deletable = true
            ),
            typesRepo,
            dbDataService,
            permsComponent,
            null
        )
    }
}
