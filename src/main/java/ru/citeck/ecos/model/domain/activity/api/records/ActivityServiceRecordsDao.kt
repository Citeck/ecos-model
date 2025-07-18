package ru.citeck.ecos.model.domain.activity.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.model.domain.activity.config.ActivityConfiguration
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Component
class ActivityServiceRecordsDao : AbstractRecordsDao(), RecordsQueryDao {

    companion object {
        const val ID = "activity-service"
        private const val LANGUAGE_TOTAL_ACTIVITIES = "total-activities"
    }

    override fun getId(): String {
        return ID
    }

    override fun queryRecords(recsQuery: RecordsQuery): Any? {
        return when (recsQuery.language) {
            LANGUAGE_TOTAL_ACTIVITIES -> getTotalActivities(recsQuery)
            else -> null
        }
    }

    private fun getTotalActivities(recsQuery: RecordsQuery): RecsQueryRes<EntityRef> {
        val targetQuery = recsQuery.copy()
            .withSourceId(ActivityConfiguration.ACTIVITY_DAO_ID)
            .withLanguage(PredicateService.LANGUAGE_PREDICATE)
            .build()
        val queryRes = AuthContext.runAsSystem {
            recordsService.query(targetQuery)
        }
        queryRes.setRecords(null)
        return queryRes
    }
}
