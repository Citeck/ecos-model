package ru.citeck.ecos.model.type.eapps.handler

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import ru.citeck.ecos.apps.app.domain.handler.ArtifactDeployMeta
import ru.citeck.ecos.model.lib.workspace.IdInWs
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.model.type.service.TypesService
import ru.citeck.ecos.webapp.api.EcosWebAppApi
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef

class TypeArtifactHandlerTest {

    private val ws = "custom"
    private val wsPrefix = "custom-sys-id:"

    private val typeService = mock<TypesService>()
    private val handler = TypeArtifactHandler(typeService, mock<EcosWebAppApi>(), fakeWorkspaceService())

    @Test
    fun `deploy rebinds CURRENT_WS, promotes co-deployed and keeps global refs`() {
        val artifact = TypeDef.create()
            .withId("my-type")
            .withFormRef(EntityRef.valueOf("uiserv/form@my-form"))
            .withJournalRef(EntityRef.valueOf("uiserv/journal@CURRENT_WS:my-journal"))
            .withNumTemplateRef(EntityRef.valueOf("emodel/num-template@global-num"))
            .build()

        val coDeployed = listOf(EntityRef.valueOf("uiserv/form@my-form"))

        val saved = deployWithCoDeployed(artifact, coDeployed)

        // co-deployed unprefixed ref → promoted to ws
        assertThat(saved.formRef.toString()).isEqualTo("uiserv/form@${wsPrefix}my-form")
        // CURRENT_WS placeholder → rebound to ws
        assertThat(saved.journalRef.toString()).isEqualTo("uiserv/journal@${wsPrefix}my-journal")
        // not co-deployed → stays global
        assertThat(saved.numTemplateRef.toString()).isEqualTo("emodel/num-template@global-num")
        assertThat(saved.workspace).isEqualTo(ws)
    }

    @Test
    fun `deploy without co-deployed leaves unprefixed refs untouched`() {
        val artifact = TypeDef.create()
            .withId("my-type")
            .withFormRef(EntityRef.valueOf("uiserv/form@my-form"))
            .build()

        val saved = deployWithCoDeployed(artifact, emptyList())

        assertThat(saved.formRef.toString()).isEqualTo("uiserv/form@my-form")
    }

    private fun deployWithCoDeployed(artifact: TypeDef, coDeployed: List<EntityRef>): TypeDef {
        val meta = ArtifactDeployMeta.create().withCoDeployedArtifacts(coDeployed).build()
        ArtifactDeployMeta.doWithMeta(meta) {
            handler.deployArtifact(artifact, ws)
        }
        val captor = argumentCaptor<TypeDef>()
        org.mockito.kotlin.verify(typeService).save(captor.capture())
        return captor.firstValue
    }

    private fun fakeWorkspaceService(): WorkspaceService {
        val service = mock<WorkspaceService>()
        whenever(service.replaceCurrentWsPlaceholderToWsPrefix(any(), any())).doAnswer { inv ->
            val id = inv.getArgument<String>(0)
            val workspace = inv.getArgument<String>(1)
            val ph = "CURRENT_WS${IdInWs.WS_DELIM}"
            if (!id.startsWith(ph) || workspace != ws) id else "$wsPrefix${id.substring(ph.length)}"
        }
        whenever(service.addWsPrefixToId(any(), any())).doAnswer { inv ->
            val localId = inv.getArgument<String>(0)
            val workspace = inv.getArgument<String>(1)
            if (workspace != ws || localId.startsWith(wsPrefix)) localId else "$wsPrefix$localId"
        }
        return service
    }
}
