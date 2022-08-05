package ru.citeck.ecos.model.type.artifacts

import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.ecostype.service.ModelTypeArtifactResolver
import ru.citeck.ecos.model.type.service.TypesService
import ru.citeck.ecos.records2.RecordRef

@Component
class TypeArtifactsResolverImpl(
    private val typeService: TypesService
) : ModelTypeArtifactResolver {

    companion object {
        val log = KotlinLogging.logger {}
    }

    override fun getTypeArtifacts(typeRef: RecordRef): List<RecordRef> {
        val artifacts = getTypeArtifactsImpl(typeRef)
        log.info("GetTypeArtifacts result: $artifacts")
        return artifacts
    }

    private fun getTypeArtifactsImpl(typeRef: RecordRef): List<RecordRef> {

        val type = typeService.getByIdOrNull(typeRef.id)
        val result = mutableListOf<RecordRef>()

        if (type == null) {
            return result
        }
        if (RecordRef.isNotEmpty(type.formRef)) {
            result.add(type.formRef)
        }

        result.addAll(type.actions.filter { RecordRef.isNotEmpty(it) })

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
