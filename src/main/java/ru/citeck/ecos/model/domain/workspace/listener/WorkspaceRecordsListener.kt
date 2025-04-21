package ru.citeck.ecos.model.domain.workspace.listener

import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.config.lib.consumer.bean.EcosConfig
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.data.sql.records.listener.*
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceMemberDesc
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMemberRole
import ru.citeck.ecos.model.domain.wstemplate.service.WorkspaceTemplateService
import ru.citeck.ecos.model.lib.authorities.AuthorityType
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

        const val CREATOR_MEMBER_ID = "creator"
    }

    @EcosConfig("wiki-initial-page-content")
    private lateinit var initialPageContent: InitialPageContent

    override fun onCreated(event: DbRecordCreatedEvent) {
        AuthContext.runAsSystem {
            applyTemplate(event)
            createWikiRoot(event.globalRef)
            createCreatorMember(event.globalRef)
        }
    }

    fun createCreatorMember(workspaceRef: EntityRef) {
        if (AuthContext.isSystemAuth(AuthContext.getCurrentFullAuth())) {
            return
        }
        recordsService.create(
            WorkspaceMemberDesc.SOURCE_ID,
            mapOf(
                WorkspaceMemberDesc.ATT_MEMBER_ID to CREATOR_MEMBER_ID,
                RecordConstants.ATT_PARENT to workspaceRef,
                RecordConstants.ATT_PARENT_ATT to WorkspaceDesc.ATT_WORKSPACE_MEMBERS,
                WorkspaceMemberDesc.ATT_AUTHORITIES to AuthorityType.PERSON.getRef(AuthContext.getCurrentUser()),
                WorkspaceMemberDesc.ATT_MEMBER_ROLE to WorkspaceMemberRole.MANAGER
            )
        )
    }

    fun createWikiRoot(workspaceRef: EntityRef) {
        val wsId = workspaceRef.getLocalId()
        val userLocale = Locale.of(I18nContext.getLocale().language)
        recordsService.create(
            WIKI_SOURCE_ID,
            mapOf(
                "id" to "$wsId\$ROOT",
                "title" to initialPageContent.title.getClosest(userLocale),
                "text" to initialPageContent.text.getClosest(userLocale),
                RecordConstants.ATT_PARENT to workspaceRef,
                RecordConstants.ATT_PARENT_ATT to WIKI_ROOT_ASSOC,
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
