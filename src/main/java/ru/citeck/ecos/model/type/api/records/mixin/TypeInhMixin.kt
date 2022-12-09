package ru.citeck.ecos.model.type.api.records.mixin

import org.springframework.stereotype.Component
import ru.citeck.ecos.model.lib.type.dto.CreateVariantDef
import ru.citeck.ecos.model.type.api.records.TypesRepoRecordsDao
import ru.citeck.ecos.model.type.service.TypesService
import ru.citeck.ecos.records3.record.atts.value.AttValue
import ru.citeck.ecos.records3.record.atts.value.AttValueCtx
import ru.citeck.ecos.records3.record.mixin.AttMixin

@Component
final class TypeInhMixin(
    private val typesService: TypesService,
    private val typesRepoRecordsDao: TypesRepoRecordsDao
) : AttMixin {

    companion object {
        private val ATTRIBUTES = setOf(
            "localId",
            "moduleId",
            "extId",
            "parent",
            "modelRoles",
            "modelStatuses",
            "modelStages",
            "modelAttributes",
            "parentModelAttributes",
            "docLibEnabled",
            "docLibFileTypeRefs",
            "docLibDirTypeRef",
            "createVariantsById"
        )
    }

    init {
        typesRepoRecordsDao.addAttributesMixin(this)
    }

    override fun getAtt(path: String, value: AttValueCtx): Any? {

        val localId = value.getLocalId()
        if (localId.isEmpty()) {
            return null
        }
        val typeDef = typesRepoRecordsDao.getRecordAtts(value.getLocalId())?.typeDef
            ?: error("Type doesn't found in repo: '${value.getLocalId()}'")

        return when (path) {
            "parent" -> typeDef.parentRef
            "moduleId", "extId", "localId" -> {
                localId
            }
            "modelRoles" -> {
                typeDef.model.roles
            }
            "modelStatuses" -> {
                typeDef.model.statuses
            }
            "modelStages" -> {
                typeDef.model.stages
            }
            "modelAttributes" -> {
                typeDef.model.attributes
            }
            "parentModelAttributes" -> {
                typesService.getInhAttributes(typeDef.parentRef.id)
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
            "createVariantsById" -> {
                CvByIdValue(typeDef.createVariants)
            }
            else -> {
                null
            }
        }
    }

    override fun getProvidedAtts(): Collection<String> {
        return ATTRIBUTES
    }

    private class CvByIdValue(val variants: List<CreateVariantDef>) : AttValue {
        override fun getAtt(name: String): Any? {
            return variants.find { it.id == name }
        }
    }
}
