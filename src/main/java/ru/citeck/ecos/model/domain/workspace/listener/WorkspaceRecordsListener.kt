package ru.citeck.ecos.model.domain.workspace.listener

import org.springframework.stereotype.Component
import ru.citeck.ecos.data.sql.records.listener.*
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.domain.wstemplate.service.WorkspaceTemplateService
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.webapp.api.entity.toEntityRef

@Component
class WorkspaceRecordsListener(
    private val wsTemplateService: WorkspaceTemplateService,
    private val recordsService: RecordsService
) : DbRecordsListenerAdapter() {

    override fun onCreated(event: DbRecordCreatedEvent) {

        val templateRef = recordsService.getAtt(
            event.record,
            WorkspaceDesc.ATT_TEMPLATE_REF + ScalarType.ID_SCHEMA
        ).asText().toEntityRef()

        if (templateRef.isEmpty()) {
            return
        }

        wsTemplateService.deployArtifactsForWorkspace(event.globalRef.getLocalId(), templateRef)
    }
}
