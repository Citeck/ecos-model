package ru.citeck.ecos.model.type.api.records.mixin

import org.springframework.stereotype.Component
import ru.citeck.ecos.model.type.api.records.ResolvedTypeRecordsDao
import ru.citeck.ecos.model.type.api.records.TypeRecordsDao
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.record.atts.value.AttValueCtx
import ru.citeck.ecos.records3.record.mixin.AttMixin

@Component
final class TypeInhMixin(
    private val resolvedTypeRecordsDao: ResolvedTypeRecordsDao,
    typeRecordsDao: TypeRecordsDao
) : AttMixin {

    companion object {
        private val ATTRIBUTES = setOf(
            "moduleId",
            "extId",
            "parent",
            RecordConstants.ATT_PARENT,
            RecordConstants.ATT_ACTIONS,
            "assocsFull",
            "form",
            "inhFormRef",
            "journal",
            "attributes",
            "inhDashboardType",
            "inhCreateVariants",
            "inhNumTemplateRef",
            "inhDispNameTemplate",
            "isSystem",
            "inhConfigFormRef",
            "inhAttributes",
            "inhSourceId",
            "resolvedModel",
            "modelRoles",
            "modelStatuses",
            "modelAttributes",
            "parentModelAttributes",
            "docLibEnabled",
            "docLibFileTypeRefs",
            "docLibDirTypeRef",
            "resolvedDocLib",
            "ecosTypeContentConfig"
        )
    }

    init {
        typeRecordsDao.addAttributesMixin(this)
        resolvedTypeRecordsDao.addAttributesMixin(this)
    }

    override fun getAtt(path: String, value: AttValueCtx): Any? {

        val rtypeDef = resolvedTypeRecordsDao.getResolvedTypeRecord(value.getLocalId())
        val typeDef = rtypeDef.typeRec.typeDef

        return when (path) {
            "moduleId", "extId", "localId" -> {
                typeDef.id
            }
            "parent",
            RecordConstants.ATT_PARENT -> {
                typeDef.parentRef
            }
            RecordConstants.ATT_ACTIONS -> {
                rtypeDef.getActions()
            }
            "assocsFull" -> {
                rtypeDef.getAssociations()
            }
            "form" -> {
                typeDef.formRef
            }
            "inhFormRef" -> {
                rtypeDef.getFormRef()
            }
            "journal" -> {
                typeDef.journalRef
            }
            "attributes" -> {
                typeDef.properties
            }
            "inhDashboardType" -> {
                rtypeDef.getDashboardType()
            }
            "inhCreateVariants" -> {
                rtypeDef.getCreateVariants()
            }
            "inhNumTemplateRef" -> {
                rtypeDef.getNumTemplateRef()
            }
            "isSystem" -> {
                typeDef.system
            }
            "inhConfigFormRef" -> {
                rtypeDef.getConfigFormRef()
            }
            "inhAttributes" -> {
                rtypeDef.getProperties()
            }
            "inhSourceId" -> {
                rtypeDef.getSourceId()
            }
            "inhDispNameTemplate" -> {
                rtypeDef.getDispNameTemplate()
            }
            "resolvedModel" -> {
                rtypeDef.getModel()
            }
            "modelRoles" -> {
                typeDef.model.roles
            }
            "modelStatuses" -> {
                typeDef.model.statuses
            }
            "modelAttributes" -> {
                typeDef.model.attributes
            }
            "parentModelAttributes" -> {
                rtypeDef.getParentRef().id.let { parentId ->
                    resolvedTypeRecordsDao.getResolvedTypeRecord(parentId).getModel().attributes
                }
            }
            "docLibEnabled" -> {
                typeDef.docLib.enabled
            }
            "docLibFileTypeRefs" -> {
                typeDef.docLib.fileTypeRefs
            }
            "docLibDirTypeRef" -> {
                typeDef.docLib.dirTypeRef
            }
            "resolvedDocLib" -> {
                rtypeDef.getDocLib()
            }
            "ecosTypeContentConfig" -> {
                typeDef.contentConfig
            }
            else -> {
                null
            }
        }
    }

    override fun getProvidedAtts(): Collection<String> {
        return ATTRIBUTES
    }
}
