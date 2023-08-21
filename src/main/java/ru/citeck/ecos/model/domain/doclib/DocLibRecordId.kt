package ru.citeck.ecos.model.domain.doclib

import org.apache.commons.lang.StringUtils
import ru.citeck.ecos.webapp.api.entity.EntityRef

class DocLibRecordId(
    val typeId: String,
    val entityRef: EntityRef
) {
    companion object {

        const val TYPE_DELIM = "$"

        fun valueOf(id: String?): DocLibRecordId {
            if (id == null || StringUtils.isBlank(id)) {
                return DocLibRecordId("", EntityRef.EMPTY)
            }
            if (!id.contains(TYPE_DELIM)) {
                return DocLibRecordId("", EntityRef.EMPTY)
            }
            val delimIdx = id.indexOf(TYPE_DELIM)
            if (delimIdx <= 0) {
                error("Invalid entityId: '$id'")
            }
            val typeId = id.substring(0, delimIdx)
            val localId = id.substring(delimIdx + 1)
            return DocLibRecordId(typeId, EntityRef.valueOf(localId))
        }
    }

    fun withEntityRef(entityRef: EntityRef): DocLibRecordId {
        return DocLibRecordId(typeId, entityRef)
    }

    override fun toString(): String {
        return "$typeId$TYPE_DELIM$entityRef"
    }
}
