package ru.citeck.ecos.model.type.api.records.mixin

import org.springframework.stereotype.Component
import ru.citeck.ecos.model.lib.type.dto.CreateVariantDef
import ru.citeck.ecos.model.type.api.records.TypesRepoRecordsDao
import ru.citeck.ecos.records3.record.atts.value.AttValue
import ru.citeck.ecos.records3.record.atts.value.AttValueCtx
import ru.citeck.ecos.records3.record.mixin.AttMixin
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import ru.citeck.ecos.webapp.lib.registry.EcosRegistry

@Component
final class TypeInhMixin(
    private val typesRegistry: EcosRegistry<TypeDef>,
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

        val registryTypeDef = typesRegistry.getValue(value.getLocalId())
            ?: error("Type doesn't found in registry: '${value.getLocalId()}'")
        val typeDef = typesRepoRecordsDao.getRecordAtts(value.getLocalId())?.typeDef
            ?: error("Type doesn't found in repo: '${value.getLocalId()}'")

        return when (path) {
            "parent" -> typeDef.parentRef
            "moduleId", "extId", "localId" -> {
                registryTypeDef.id
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
                registryTypeDef.parentRef.id.let { parentId ->
                    typesRegistry.getValue(parentId)?.model?.attributes ?: emptyList()
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
