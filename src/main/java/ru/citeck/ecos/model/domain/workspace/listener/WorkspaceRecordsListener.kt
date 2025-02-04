package ru.citeck.ecos.model.domain.workspace.listener

import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.data.sql.records.listener.*
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.domain.wstemplate.service.WorkspaceTemplateService
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.toEntityRef

@Component
class WorkspaceRecordsListener(
    private val wsTemplateService: WorkspaceTemplateService,
    private val recordsService: RecordsService
) : DbRecordsListenerAdapter() {

    companion object {
        private const val WIKI_SOURCE_ID = "${AppName.EMODEL}/wiki"
        private const val WIKI_ROOT_ASSOC = "wikiRoot"

        private val INITIAL_PAGE_TITLE = MLText(
            I18nContext.ENGLISH to "Welcome!",
            I18nContext.RUSSIAN to "Добро пожаловать!"
        )
        private val INITIAL_PAGE_TEXT = MLText(
            I18nContext.ENGLISH to "Welcome to wiki!",
            I18nContext.RUSSIAN to "Добро пожаловать в базу знаний!"
        )
    }

    override fun onCreated(event: DbRecordCreatedEvent) {
        applyTemplate(event)
        createWikiRoot(event)
    }

    private fun createWikiRoot(event: DbRecordCreatedEvent) {

        val wsId = event.globalRef.getLocalId()
        val wikiRootRef = recordsService.create(
            WIKI_SOURCE_ID,
            mapOf(
                "id" to "$wsId\$ROOT",
                "title" to "ROOT",
                "text" to "ROOT",
                //RecordConstants.ATT_PARENT to event.globalRef,
                //RecordConstants.ATT_PARENT_ATT to WIKI_ROOT_ASSOC,
                RecordConstants.ATT_WORKSPACE to wsId
            )
        )
        recordsService.create(
            WIKI_SOURCE_ID,
            mapOf(
                RecordConstants.ATT_PARENT to wikiRootRef,
                RecordConstants.ATT_PARENT_ATT to "children",
                "title" to INITIAL_PAGE_TITLE,
                "text" to INITIAL_PAGE_TEXT,
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
}
