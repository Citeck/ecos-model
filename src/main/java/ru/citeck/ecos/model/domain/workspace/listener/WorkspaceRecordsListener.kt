package ru.citeck.ecos.model.domain.workspace.listener

import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.config.lib.consumer.bean.EcosConfig
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.data.sql.records.listener.*
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.domain.wstemplate.service.WorkspaceTemplateService
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.entity.toEntityRef
import java.util.*

@Component
class WorkspaceRecordsListener(
    private val wsTemplateService: WorkspaceTemplateService,
    private val recordsService: RecordsService
) : DbRecordsListenerAdapter() {

    companion object {
        private const val WIKI_SOURCE_ID = "${AppName.EMODEL}/wiki"
        private const val WIKI_ROOT_ASSOC = "wikiRoot"
    }

    @EcosConfig("wiki-initial-page-content")
    private lateinit var initialPageContent: InitialPageContent

    override fun onCreated(event: DbRecordCreatedEvent) {
        applyTemplate(event)
        createWikiRoot(event.globalRef)
    }

    fun createWikiRoot(workspaceRef: EntityRef) {

        val wsId = workspaceRef.getLocalId()
        val wikiRootRef = recordsService.create(
            WIKI_SOURCE_ID,
            mapOf(
                "id" to "$wsId\$ROOT",
                "title" to "ROOT",
                "text" to "ROOT",
                RecordConstants.ATT_PARENT to workspaceRef,
                RecordConstants.ATT_PARENT_ATT to WIKI_ROOT_ASSOC,
                RecordConstants.ATT_WORKSPACE to wsId
            )
        )
        val userLocale = Locale.of(I18nContext.getLocale().language)
        recordsService.create(
            WIKI_SOURCE_ID,
            mapOf(
                RecordConstants.ATT_PARENT to wikiRootRef,
                RecordConstants.ATT_PARENT_ATT to "children",
                "title" to initialPageContent.title.getClosest(userLocale),
                "text" to initialPageContent.text.getClosest(userLocale),
                RecordConstants.ATT_WORKSPACE to wsId
            )
        )
    }

    private fun applyTemplate(event: DbRecordCreatedEvent) {

        val templateRef = recordsService.getAtt(
            event.record,
            WorkspaceDesc.ATT_TEMPLATE_REF + ScalarType.ID_SCHEMA
        ).asText().toEntityRef()

        if (templateRef.isEmpty()) {
            return
        }

        val wsId = event.globalRef.getLocalId()
        wsTemplateService.deployArtifactsForWorkspace(wsId, templateRef)
    }

    class InitialPageContent(
        val title: MLText,
        val text: MLText
    )
}
