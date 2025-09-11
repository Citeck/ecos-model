package ru.citeck.ecos.model.type.artifacts

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.ecostype.service.ModelTypeArtifactResolver
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.model.type.service.TypeId.Companion.convertToTypeId
import ru.citeck.ecos.model.type.service.TypesService
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Component
class TypeArtifactsResolverImpl(
    private val typeService: TypesService,
    private val workspaceService: WorkspaceService? = null
) : ModelTypeArtifactResolver {

    companion object {
        val log = KotlinLogging.logger {}
    }

    override fun getTypeArtifacts(typeRef: EntityRef): List<EntityRef> {
        val artifacts = getTypeArtifactsImpl(typeRef)
        log.info { "GetTypeArtifacts result: $artifacts" }
        return artifacts
    }

    private fun getTypeArtifactsImpl(typeRef: EntityRef): List<EntityRef> {

        val type = typeService.getByIdOrNull(workspaceService.convertToTypeId(typeRef.getLocalId()))
        val result = mutableListOf<EntityRef>()

        if (type == null) {
            return result
        }
        if (EntityRef.isNotEmpty(type.formRef)) {
            result.add(type.formRef)
        }

        result.addAll(type.actions.filter { EntityRef.isNotEmpty(it) })

        if (EntityRef.isNotEmpty(type.journalRef)) {
            result.add(type.journalRef)
        }
        if (EntityRef.isNotEmpty(type.numTemplateRef)) {
            result.add(type.numTemplateRef)
        }
        if (EntityRef.isNotEmpty(type.configFormRef)) {
            result.add(type.configFormRef)
        }

        return result
    }
}
