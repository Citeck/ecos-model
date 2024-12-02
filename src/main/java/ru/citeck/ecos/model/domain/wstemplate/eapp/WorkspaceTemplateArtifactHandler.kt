package ru.citeck.ecos.model.domain.wstemplate.eapp

import org.springframework.stereotype.Component
import ru.citeck.ecos.apps.app.domain.handler.EcosArtifactHandler
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.model.domain.wstemplate.desc.WorkspaceTemplateDesc
import ru.citeck.ecos.model.domain.wstemplate.listener.WorkspaceTemplateRecordsListener
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import java.util.*
import java.util.function.Consumer

@Component
class WorkspaceTemplateArtifactHandler(
    private val recordsService: RecordsService,
    private val workspaceTemplateRecordsListener: WorkspaceTemplateRecordsListener
) : EcosArtifactHandler<WorkspaceTemplateArtifactHandler.WorkspaceTemplateArtifact> {

    override fun deployArtifact(artifact: WorkspaceTemplateArtifact) {
        val atts = RecordAtts(WorkspaceTemplateDesc.getRef(""))
        artifact.meta.forEach { k, value ->
            atts[k] = value
        }
        atts[RecordConstants.ATT_ID] = artifact.id
        atts[WorkspaceTemplateDesc.ATT_ARTIFACTS] = artifact.artifacts
        recordsService.mutate(atts)
    }

    override fun deleteArtifact(artifactId: String) {
        recordsService.delete(WorkspaceTemplateDesc.getRef(artifactId))
    }

    override fun getArtifactType(): String {
        return "model/workspace-template"
    }

    override fun listenChanges(listener: Consumer<WorkspaceTemplateArtifact>) {
        workspaceTemplateRecordsListener.listenCreatedOrUpdated {
            val atts = recordsService.getAtts(it, TemplateAttsOnChanged::class.java)
            val artifactsBase64 = (atts.artifacts ?: "")
            val artifactsBytes = if (artifactsBase64.isBlank()) {
                null
            } else {
                Base64.getDecoder().decode(artifactsBase64)
            }
            listener.accept(
                WorkspaceTemplateArtifact(
                    atts.id,
                    atts.meta?.getData() ?: DataValue.createObj(),
                    artifactsBytes
                )
            )
        }
    }

    private class TemplateAttsOnChanged(
        val id: String,
        val meta: ObjectData?,
        @AttName(WorkspaceTemplateDesc.ATT_ARTIFACTS + ScalarType.STR_SCHEMA)
        val artifacts: String?
    )

    class WorkspaceTemplateArtifact(
        val id: String,
        val meta: DataValue = DataValue.createObj(),
        val artifacts: ByteArray? = null
    )
}
