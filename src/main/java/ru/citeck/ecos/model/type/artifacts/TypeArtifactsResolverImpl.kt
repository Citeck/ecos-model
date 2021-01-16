package ru.citeck.ecos.model.type.artifacts

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.ecostype.service.ModelTypeArtifactResolver
import ru.citeck.ecos.model.type.service.TypeService
import ru.citeck.ecos.records2.RecordRef

@Component
class TypeArtifactsResolverImpl(
    private val typeService: TypeService
) : ModelTypeArtifactResolver {

    override fun getTypeArtifacts(typeRef: RecordRef): List<RecordRef> {

        val type = typeService.getByExtId(typeRef.id)
        val result = mutableListOf<RecordRef>()

        if (type == null) {
            return result
        }
        if (RecordRef.isNotEmpty(type.formRef)) {
            result.add(type.formRef)
        }
        if (type.actions != null) {
            result.addAll(type.actions.filter { RecordRef.isNotEmpty(it) })
        }
        if (RecordRef.isNotEmpty(type.journalRef)) {
            result.add(type.journalRef)
        }
        if (RecordRef.isNotEmpty(type.numTemplateRef)) {
            result.add(type.numTemplateRef)
        }
        if (RecordRef.isNotEmpty(type.configFormRef)) {
            result.add(type.configFormRef)
        }

        return result
    }
}
