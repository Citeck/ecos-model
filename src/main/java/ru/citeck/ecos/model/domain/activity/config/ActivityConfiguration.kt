package ru.citeck.ecos.model.domain.activity.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records3.RecordsService
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
            .build()

        return dao
    }
}

