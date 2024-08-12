package ru.citeck.ecos.model.type.api.records.mixin

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.model.type.api.records.TypesRepoRecordsDao
import ru.citeck.ecos.records3.record.atts.value.AttValueCtx
import ru.citeck.ecos.records3.record.mixin.AttMixin
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.type.records.TypeRecordsDao

@Component
class TypesRegistryMixin(
    private val typeRecordsDao: TypeRecordsDao
) : AttMixin {

    companion object {
        const val REPO_TYPE_REF = "repoTypeRef"
        const val DOC_LIB_INFO = "docLibInfo"
    }

    @PostConstruct
    fun init() {
        typeRecordsDao.addAttributesMixin(this)
    }

    override fun getAtt(path: String, value: AttValueCtx): Any? {
        when (path) {
            REPO_TYPE_REF -> EntityRef.create(TypesRepoRecordsDao.ID, value.getLocalId())
            // temp solution until aspects config can be calculated by custom logic
            DOC_LIB_INFO -> {
                val config = value.getAtt("aspectById.doclib.config?json")
                if (config.isNull()) {
                    return DocLibInfo(false, emptyList(), EntityRef.EMPTY)
                }
                val fileTypeRefs = config["fileTypeRefs"].asList(EntityRef::class.java)
                if (fileTypeRefs.isEmpty()) {
                    fileTypeRefs.add(value.getRef())
                }
                val dirTypeRef = config["dirTypeRef"].asText().ifBlank {
                    ModelUtils.getTypeRef("doclib-directory")
                }
                return DocLibInfo(true, fileTypeRefs, EntityRef.valueOf(dirTypeRef))
            }
        }
        return null
    }

    override fun getProvidedAtts(): Collection<String> {
        return listOf(REPO_TYPE_REF, DOC_LIB_INFO)
    }

    class DocLibInfo(
        val enabled: Boolean,
        val fileTypeRefs: List<EntityRef>,
        val dirTypeRef: EntityRef
    )
}
