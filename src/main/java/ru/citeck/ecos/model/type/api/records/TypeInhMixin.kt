package ru.citeck.ecos.model.type.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.record.atts.value.AttValueCtx
import ru.citeck.ecos.records3.record.mixin.AttMixin

@Component
class TypeInhMixin(
    private val resolvedTypeRecordsDao: ResolvedTypeRecordsDao
) : AttMixin {

    companion object {
        private val ATTRIBUTES = setOf(
            "moduleId",
            "extId",
            "parent",
            RecordConstants.ATT_PARENT,
            "parents",
            "children",
            RecordConstants.ATT_ACTIONS,
            "associations",
            "assocsFull",
            "form",
            "inhFormRef",
            "journal",
            "attributes",
            "inhDashboardType",
            "inhCreateVariants",
            "isSystem",
            "inhConfigFormRef",
            "inhAttributes",
            "inhSourceId",
            "resolvedModel",
            "modelRoles",
            "modelStatuses",
            "modelAttributes",
            "docLibEnabled",
            "docLibFileTypeRefs",
            "docLibDirTypeRef",
            "resolvedDocLib",
            "data"
        )
    }

    override fun getAtt(path: String, value: AttValueCtx): Any? {
        return when (path) {
            "moduleId", "extId", "localId" -> {
                value.getLocalId()
            }
            "parent",
            RecordConstants.ATT_PARENT -> {
                RecordRef.valueOf(value.getAtt("parentRef?id").asText())
            }
            "parents" -> {
                emptyList<Any>()//todo
            }
            "children" -> {
                emptyList<Any>()//todo
            }
            RecordConstants.ATT_ACTIONS -> {
                val rec = resolvedTypeRecordsDao.getResolvedTypeRecord(value.getLocalId())
                return rec.getActions()
            }
            "associations",
            "assocsFull" -> {
                return emptyList<Any>()
            }
            "form" -> {

            }
            "inhFormRef",
            "journal",
            "attributes",
            "inhDashboardType",
            "inhCreateVariants",
            "isSystem",
            "inhConfigFormRef",
            "inhAttributes",
            "inhSourceId",
            "resolvedModel",
            "modelRoles",
            "modelStatuses",
            "modelAttributes",
            "docLibEnabled",
            "docLibFileTypeRefs",
            "docLibDirTypeRef",
            "resolvedDocLib",
            "data" -> {null}
            else -> {
                null
            }
        }
    }

    override fun getProvidedAtts(): Collection<String> {
        return ATTRIBUTES
    }
}
