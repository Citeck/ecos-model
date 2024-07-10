package ru.citeck.ecos.model.domain.activity.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery

@Component
class ActivityTypeRecordDao : AbstractRecordsDao(), RecordsQueryDao {

    companion object {
        const val ID = "activity-type"
        private val ACTIVITY_TYPE_REF = ModelUtils.getTypeRef("ecos-activity")
        private const val PARENTS_ATT = "parents"
    }

    override fun getId(): String {
        return ID
    }

    override fun queryRecords(recsQuery: RecordsQuery): Any? {
        return AuthContext.runAsSystem {
            recordsService.query(
                RecordsQuery.create()
                    .withSourceId(ACTIVITY_TYPE_REF.getSourceId())
                    .withLanguage(PredicateService.LANGUAGE_PREDICATE)
                    .withQuery(
                        Predicates.contains(PARENTS_ATT, ACTIVITY_TYPE_REF.toString())
                    )
                    .withSortBy(recsQuery.sortBy)
                    .withGroupBy(recsQuery.groupBy)
                    .withPage(recsQuery.page)
                    .build()
            )
        }
    }
}
