package ru.citeck.ecos.model.domain.activity.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.constants.AppName

@Component
class ActivityTypeRecordDao : AbstractRecordsDao(), RecordsQueryDao {

    companion object {
        const val ID = "activity-type"
        private const val TYPE_SOURCE_ID = "${AppName.EMODEL}/type"
        private const val PLANNED_ACTIVITY = "$TYPE_SOURCE_ID@planned-activity"
        private const val IMMEDIATE_ACTIVITY = "$TYPE_SOURCE_ID@immediate-activity"
        private const val PARENT_ATT = "parent"
    }

    override fun getId(): String {
        return ID
    }

    override fun queryRecords(recsQuery: RecordsQuery): Any? {
        return AuthContext.runAsSystem {
            recordsService.query(
                RecordsQuery.create()
                    .withSourceId(TYPE_SOURCE_ID)
                    .withLanguage(PredicateService.LANGUAGE_PREDICATE)
                    .withQuery(
                        Predicates.or(
                            Predicates.eq(PARENT_ATT, PLANNED_ACTIVITY),
                            Predicates.eq(PARENT_ATT, IMMEDIATE_ACTIVITY)
                        )
                    )
                    .withSortBy(recsQuery.sortBy)
                    .withGroupBy(recsQuery.groupBy)
                    .withPage(recsQuery.page)
                    .build()
            )
        }
    }
}
