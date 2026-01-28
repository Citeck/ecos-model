package ru.citeck.ecos.model.num.api.records

import org.springframework.security.access.annotation.Secured
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.model.num.service.NumTemplateService
import ru.citeck.ecos.records3.record.dao.mutate.ValueMutateDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Component
class NumTemplateActionRecordsDao(
    private val numTemplateService: NumTemplateService
) : ValueMutateDao<NumTemplateActionRecordsDao.ActionDao>,
    RecordsQueryDao {

    companion object {
        const val ID = "num-template-action"
    }

    override fun queryRecords(recsQuery: RecordsQuery): Any? {
        when (recsQuery.language) {
            "get-next-number" -> {
                val args = recsQuery.getQuery(GetNextNumberQuery::class.java)
                return numTemplateService.getNextNumber(args.templateRef, args.counterKey, false)
            }
            else -> error("Unknown query action: ${recsQuery.language}")
        }
    }

    @Secured(AuthRole.ADMIN, AuthRole.SYSTEM)
    override fun mutate(value: ActionDao): Any? {
        return when (value.type) {
            "set-next-number" -> {
                val args = value.args.getAsNotNull(SetNextNumberAction::class.java)
                numTemplateService.setNextNumber(args.templateRef, args.counterKey, args.nextNumber)
                DataValue.createObj()
            }
            else -> error("Unknown action: " + value.type)
        }
    }

    override fun getId(): String {
        return ID
    }

    class SetNextNumberAction(
        val templateRef: EntityRef,
        val counterKey: String,
        val nextNumber: Long
    )

    class GetNextNumberQuery(
        val templateRef: EntityRef,
        val counterKey: String
    )

    class ActionDao(
        val type: String,
        val args: ObjectData
    )
}
