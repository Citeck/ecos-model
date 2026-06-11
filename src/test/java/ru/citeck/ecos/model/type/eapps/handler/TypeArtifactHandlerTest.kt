package ru.citeck.ecos.model.type.eapps.handler

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import ru.citeck.ecos.apps.app.domain.handler.ArtifactDeployMeta
import ru.citeck.ecos.model.lib.type.dto.CreateVariantDef
import ru.citeck.ecos.model.lib.workspace.IdInWs
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.model.type.service.TypesService
import ru.citeck.ecos.webapp.api.EcosWebAppApi
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.type.dto.AssocDef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import java.util.function.BiConsumer

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
    fun `deploy rebinds CURRENT_WS parentRef`() {
        val artifact = TypeDef.create()
            .withId("my-type")
            .withParentRef(EntityRef.valueOf("emodel/type@CURRENT_WS:my-parent"))
            .build()

        val saved = deployWithCoDeployed(artifact, emptyList())

        assertThat(saved.parentRef.toString()).isEqualTo("emodel/type@${wsPrefix}my-parent")
    }

    @Test
    fun `deploy promotes co-deployed parent type`() {
        val artifact = TypeDef.create()
            .withId("my-type")
            .withParentRef(EntityRef.valueOf("emodel/type@my-parent"))
            .build()

        val saved = deployWithCoDeployed(artifact, listOf(EntityRef.valueOf("emodel/type@my-parent")))

        assertThat(saved.parentRef.toString()).isEqualTo("emodel/type@${wsPrefix}my-parent")
    }

    @Test
    fun `deploy keeps global parent type untouched`() {
        val artifact = TypeDef.create()
            .withId("my-type")
            .withParentRef(EntityRef.valueOf("emodel/type@global-parent"))
            .build()

        val saved = deployWithCoDeployed(artifact, emptyList())

        assertThat(saved.parentRef.toString()).isEqualTo("emodel/type@global-parent")
    }

    @Test
    fun `deploy rebinds CURRENT_WS refs in associations and create variants`() {
        val artifact = TypeDef.create()
            .withId("my-type")
            .withAssociations(
                listOf(
                    AssocDef.create()
                        .withId("assoc")
                        .withTarget(EntityRef.valueOf("emodel/type@CURRENT_WS:target-type"))
                        .withJournals(listOf(EntityRef.valueOf("uiserv/journal@CURRENT_WS:assoc-journal")))
                        .build()
                )
            )
            .withCreateVariants(
                listOf(
                    CreateVariantDef.create()
                        .withId("cv")
                        .withTypeRef(EntityRef.valueOf("emodel/type@CURRENT_WS:cv-type"))
                        .withFormRef(EntityRef.valueOf("uiserv/form@CURRENT_WS:cv-form"))
                        .withPostActionRef(EntityRef.valueOf("uiserv/action@CURRENT_WS:cv-action"))
                        .build()
                )
            )
            .build()

        val saved = deployWithCoDeployed(artifact, emptyList())

        assertThat(saved.associations[0].target.toString()).isEqualTo("emodel/type@${wsPrefix}target-type")
        assertThat(saved.associations[0].journals[0].toString()).isEqualTo("uiserv/journal@${wsPrefix}assoc-journal")
        assertThat(saved.createVariants[0].typeRef.toString()).isEqualTo("emodel/type@${wsPrefix}cv-type")
        assertThat(saved.createVariants[0].formRef.toString()).isEqualTo("uiserv/form@${wsPrefix}cv-form")
        assertThat(saved.createVariants[0].postActionRef.toString()).isEqualTo("uiserv/action@${wsPrefix}cv-action")
    }

    @Test
    fun `deploy promotes co-deployed, keeps global and leaves empty nested refs untouched`() {
        val artifact = TypeDef.create()
            .withId("my-type")
            .withAssociations(
                listOf(
                    AssocDef.create()
                        .withId("assoc")
                        .withTarget(EntityRef.valueOf("emodel/type@local-target"))
                        .withJournals(listOf(EntityRef.valueOf("uiserv/journal@global-journal")))
                        .build(),
                    AssocDef.create()
                        .withId("assoc-empty")
                        .build()
                )
            )
            .withCreateVariants(
                listOf(
                    CreateVariantDef.create()
                        .withId("cv")
                        .withTypeRef(EntityRef.valueOf("emodel/type@local-target"))
                        .withFormRef(EntityRef.valueOf("uiserv/form@global-form"))
                        .build()
                )
            )
            .build()

        val saved = deployWithCoDeployed(artifact, listOf(EntityRef.valueOf("emodel/type@local-target")))

        // co-deployed → promoted to ws
        assertThat(saved.associations[0].target.toString()).isEqualTo("emodel/type@${wsPrefix}local-target")
        assertThat(saved.createVariants[0].typeRef.toString()).isEqualTo("emodel/type@${wsPrefix}local-target")
        // not co-deployed → stays global
        assertThat(saved.associations[0].journals[0].toString()).isEqualTo("uiserv/journal@global-journal")
        assertThat(saved.createVariants[0].formRef.toString()).isEqualTo("uiserv/form@global-form")
        // empty refs → untouched
        assertThat(saved.associations[1].target).isEqualTo(EntityRef.EMPTY)
        assertThat(saved.associations[1].journals).isEmpty()
        assertThat(saved.createVariants[0].postActionRef).isEqualTo(EntityRef.EMPTY)
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

    @Test
    fun `listenChanges strips ws prefix to CURRENT_WS for parentRef and nested refs`() {
        val ecosWebAppApi = mock<EcosWebAppApi>()
        doAnswer { inv ->
            inv.getArgument<() -> Unit>(1).invoke()
            null
        }.whenever(ecosWebAppApi).doWhenAppReady(any(), any())

        val handler = TypeArtifactHandler(typeService, ecosWebAppApi, fakeWorkspaceService())

        val emitted = mutableListOf<Pair<TypeDef, String>>()
        handler.listenChanges { typeDef, workspace -> emitted.add(typeDef to workspace) }

        val listenerCaptor = argumentCaptor<BiConsumer<TypeDef?, TypeDef?>>()
        org.mockito.kotlin.verify(typeService).addListener(listenerCaptor.capture())

        val changed = TypeDef.create()
            .withId("my-type")
            .withWorkspace(ws)
            .withParentRef(EntityRef.valueOf("emodel/type@${wsPrefix}my-parent"))
            .withAssociations(
                listOf(
                    AssocDef.create()
                        .withId("assoc")
                        .withTarget(EntityRef.valueOf("emodel/type@${wsPrefix}target-type"))
                        .withJournals(listOf(EntityRef.valueOf("uiserv/journal@${wsPrefix}assoc-journal")))
                        .build(),
                    AssocDef.create()
                        .withId("assoc-empty")
                        .build()
                )
            )
            .withCreateVariants(
                listOf(
                    CreateVariantDef.create()
                        .withId("cv")
                        .withTypeRef(EntityRef.valueOf("emodel/type@${wsPrefix}cv-type"))
                        .build()
                )
            )
            .build()

        listenerCaptor.firstValue.accept(null, changed)

        assertThat(emitted).hasSize(1)
        val (stripped, workspace) = emitted[0]
        assertThat(workspace).isEqualTo(ws)
        assertThat(stripped.parentRef.toString()).isEqualTo("emodel/type@CURRENT_WS:my-parent")
        assertThat(stripped.associations[0].target.toString()).isEqualTo("emodel/type@CURRENT_WS:target-type")
        assertThat(stripped.associations[0].journals[0].toString()).isEqualTo("uiserv/journal@CURRENT_WS:assoc-journal")
        assertThat(stripped.createVariants[0].typeRef.toString()).isEqualTo("emodel/type@CURRENT_WS:cv-type")
        // empty nested refs stay empty on export (applyToRef guard)
        assertThat(stripped.associations[1].target).isEqualTo(EntityRef.EMPTY)
        assertThat(stripped.associations[1].journals).isEmpty()
        assertThat(stripped.createVariants[0].postActionRef).isEqualTo(EntityRef.EMPTY)
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
        whenever(service.replaceWsPrefixToCurrentWsPlaceholder(any())).doAnswer { inv ->
            val id = inv.getArgument<String>(0)
            if (id.startsWith(wsPrefix)) "CURRENT_WS${IdInWs.WS_DELIM}${id.substring(wsPrefix.length)}" else id
        }
        return service
    }
}
