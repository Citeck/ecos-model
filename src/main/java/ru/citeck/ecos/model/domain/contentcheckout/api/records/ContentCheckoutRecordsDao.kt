package ru.citeck.ecos.model.domain.contentcheckout.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.model.domain.contentcheckout.service.ContentCheckoutService
import ru.citeck.ecos.records3.record.dao.mutate.ValueMutateDao
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Component
class ContentCheckoutRecordsDao(
    private val contentCheckoutService: ContentCheckoutService
) : ValueMutateDao<ContentCheckoutRecordsDao.ActionData> {

    companion object {
        const val ID = "content-checkout"
    }

    override fun getId(): String {
        return ID
    }

    override fun mutate(value: ActionData): Any? {
        when (value.action) {
            ActionType.CHECKOUT -> contentCheckoutService.checkout(value.recordRef)
            ActionType.CANCEL_CHECKOUT -> contentCheckoutService.cancelCheckout(value.recordRef)
            ActionType.CHECKIN -> {
                val contentRef = extractContentRef(value.content)
                    ?: error("Content is required for checkin")
                contentCheckoutService.checkin(
                    value.recordRef,
                    contentRef,
                    value.versionComment ?: "",
                    value.majorVersion ?: false
                )
            }
        }
        return DataValue.createObj()
    }

    private fun extractContentRef(content: DataValue?): EntityRef? {
        if (content == null || content.isNull()) {
            return null
        }
        // File upload component sends: [{storage, name, url, data: {entityRef: "..."}, ...}]
        val item = if (content.isArray()) content[0] else content
        val entityRefStr = item["data"]["entityRef"].asText().ifEmpty {
            item["entityRef"].asText().ifEmpty {
                item.asText()
            }
        }
        if (entityRefStr.isEmpty()) {
            return null
        }
        return EntityRef.valueOf(entityRefStr)
    }

    class ActionData(
        val action: ActionType = ActionType.CHECKOUT,
        val recordRef: EntityRef = EntityRef.EMPTY,
        val content: DataValue? = null,
        val versionComment: String? = null,
        val majorVersion: Boolean? = null
    )

    enum class ActionType {
        CHECKOUT,
        CANCEL_CHECKOUT,
        CHECKIN
    }
}
