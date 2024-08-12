package ru.citeck.ecos.model.domain.activity.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.model.domain.activity.config.ActivityConfiguration
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.PredicateUtils
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.ValuePredicate
import ru.citeck.ecos.records3.record.dao.impl.proxy.RecordsDaoProxy
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.entity.toEntityRef

@Component
class ActivityRecordsProxy : RecordsDaoProxy(
    ActivityConfiguration.ACTIVITY_DAO_ID,
    ActivityConfiguration.ACTIVITY_REPO_DAO_ID
) {

    override fun queryRecords(recsQuery: RecordsQuery): RecsQueryRes<*>? {

        if (recsQuery.language.isNotBlank() && recsQuery.language != PredicateService.LANGUAGE_PREDICATE) {
            return null
        }
        val predicate = recsQuery.getQuery(Predicate::class.java)
        var parentRef: EntityRef = EntityRef.EMPTY

        PredicateUtils.mapValuePredicates(predicate, { valuePred ->
            if (valuePred.getType() == ValuePredicate.Type.EQ &&
                valuePred.getAttribute() == RecordConstants.ATT_PARENT
            ) {
                parentRef = valuePred.getValue().asText().toEntityRef()
            }
            valuePred
        }, onlyAnd = true, optimize = false, filterEmptyComposite = false)

        if (parentRef.isEmpty()) {
            return null
        }
        val canReadParent = recordsService.getAtt(parentRef, "permissions._has.read?bool!").asBoolean()
        if (!canReadParent) {
            return null
        }
        return super.queryRecords(recsQuery)
    }
}
