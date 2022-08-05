package ru.citeck.ecos.model.type.api.records.mixin

import org.springframework.stereotype.Component
import ru.citeck.ecos.model.type.api.records.TypesRepoRecordsDao
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.record.atts.value.AttValueCtx
import ru.citeck.ecos.records3.record.mixin.AttMixin
import ru.citeck.ecos.webapp.lib.model.type.records.TypeRecordsDao
import javax.annotation.PostConstruct

@Component
class TypesRegistryMixin(
    private val typeRecordsDao: TypeRecordsDao
) : AttMixin {

    companion object {
        const val REPO_TYPE_REF = "repoTypeRef"
    }

    @PostConstruct
    fun init() {
        typeRecordsDao.addAttributesMixin(this)
    }

    override fun getAtt(path: String, value: AttValueCtx): Any? {
        if (path == REPO_TYPE_REF) {
            return RecordRef.create(TypesRepoRecordsDao.ID, value.getLocalId())
        }
        return null
    }

    override fun getProvidedAtts(): Collection<String> {
        return listOf("repoTypeRef")
    }
}
