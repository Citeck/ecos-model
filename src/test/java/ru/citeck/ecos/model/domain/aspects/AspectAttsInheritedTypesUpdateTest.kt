package ru.citeck.ecos.model.domain.aspects

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.domain.workspace.dto.Workspace
import ru.citeck.ecos.model.domain.workspace.service.EmodelWorkspaceService
import ru.citeck.ecos.model.lib.aspect.constants.AspectConstants
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef
import ru.citeck.ecos.model.lib.type.dto.TypeAspectDef
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.model.lib.workspace.IdInWs
import ru.citeck.ecos.model.type.service.TypesService
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.aspect.dto.AspectDef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension

/**
 * A type gets the attributes of an aspect either from its own aspects or from the aspects of its
 * parents. When the aspect itself changes, both kinds of types must be re-resolved: a type left
 * with the old aspect attributes silently loses the values of the new ones, because ecos-data
 * takes the columns to store from the resolved model.
 *
 * A type in a workspace may inherit from a global type (see TypeWorkspaceParentValidationTest),
 * and then it inherits the global type's aspects as well - such a child must be re-resolved too.
 */
@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosModelApp::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AspectAttsInheritedTypesUpdateTest {

    companion object {
        private const val UPDATE_TIMEOUT_MS = 30_000L
    }

    @Autowired
    private lateinit var recordsService: RecordsService

    @Autowired
    private lateinit var typesService: TypesService

    @Autowired
    private lateinit var workspaceService: EmodelWorkspaceService

    private val typesToDelete = ArrayList<IdInWs>()
    private val aspectsToDelete = ArrayList<String>()
    private val workspacesToDelete = ArrayList<EntityRef>()

    @AfterAll
    fun tearDown() {
        // children first: a type is only deletable once nothing inherits from it
        AuthContext.runAsSystem {
            typesToDelete.asReversed().forEach { typesService.delete(it) }
            aspectsToDelete.forEach { recordsService.delete(aspectRef(it)) }
            workspacesToDelete.forEach { recordsService.delete(it) }
        }
    }

    @Test
    fun aspectAttsDeliveredToTypesWhichInheritTheAspect() {

        val aspectId = "asp-inh-test"
        val prefix = "aspinh"
        saveAspect(aspectId, prefix, "a")

        // the aspect is declared by the owner only, the other two receive it through parentRef
        val owner = createType("asp-inh-owner", parentId = "base", aspectId = aspectId)
        val child = createType("asp-inh-child", parentId = "asp-inh-owner")
        val grandChild = createType("asp-inh-grand-child", parentId = "asp-inh-child")

        assertTypeModelAtts(owner, "$prefix:a")
        assertTypeModelAtts(child, "$prefix:a")
        assertTypeModelAtts(grandChild, "$prefix:a")

        saveAspect(aspectId, prefix, "a", "b")

        assertTypeModelAtts(owner, "$prefix:a", "$prefix:b")
        assertTypeModelAtts(child, "$prefix:a", "$prefix:b")
        assertTypeModelAtts(grandChild, "$prefix:a", "$prefix:b")
    }

    @Test
    fun aspectAttsDeliveredToTypeInWorkspaceWithGlobalParent() {

        val aspectId = "asp-inh-ws-test"
        val prefix = "aspinhws"
        saveAspect(aspectId, prefix, "a")

        val workspace = createWorkspace("asp-inh-ws")

        val globalOwner = createType("asp-inh-ws-owner", parentId = "base", aspectId = aspectId)
        val childInWs = createType(
            "asp-inh-ws-child",
            parentId = "asp-inh-ws-owner",
            workspace = workspace
        )

        assertTypeModelAtts(globalOwner, "$prefix:a")
        assertTypeModelAtts(childInWs, "$prefix:a")

        saveAspect(aspectId, prefix, "a", "b")

        assertTypeModelAtts(globalOwner, "$prefix:a", "$prefix:b")
        assertTypeModelAtts(childInWs, "$prefix:a", "$prefix:b")
    }

    /**
     * Types are re-resolved by a background updater, so wait for the expected state instead of
     * reading the registry once, but assert the last value read to get a meaningful message.
     */
    private fun assertTypeModelAtts(typeRef: EntityRef, vararg expectedAtts: String) {
        val deadline = System.currentTimeMillis() + UPDATE_TIMEOUT_MS
        var atts = typeModelAtts(typeRef)
        while (!atts.containsAll(expectedAtts.toList()) && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
            atts = typeModelAtts(typeRef)
        }
        assertThat(atts).describedAs("model attributes of '$typeRef'").contains(*expectedAtts)
    }

    private fun typeModelAtts(typeRef: EntityRef): List<String> {
        return AuthContext.runAsSystem {
            recordsService.getAtt(typeRef, "model.attributes[]?json")
                .asList(AttributeDef::class.java)
                .map { it.id }
        }
    }

    /**
     * @return reference to the resolved type. The local identifier of a type in a workspace is
     *   prefixed by the mutation, so it is taken from the mutation result instead of being built.
     */
    private fun createType(
        typeId: String,
        parentId: String,
        aspectId: String = "",
        workspace: String = ""
    ): EntityRef {
        val typeData = ObjectData.create()
            .set("id", typeId)
            .set("parentRef", ModelUtils.getTypeRef(parentId))
        if (aspectId.isNotEmpty()) {
            typeData["aspects"] = listOf(TypeAspectDef.create { withRef(ModelUtils.getAspectRef(aspectId)) })
        }
        if (workspace.isNotEmpty()) {
            typeData["workspace"] = workspace
        }
        val createdRef = AuthContext.runAsSystem {
            recordsService.mutate(typeRepoRef(""), typeData)
        }
        typesToDelete.add(IdInWs.create(workspace, typeId))
        return ModelUtils.getTypeRef(createdRef.getLocalId())
    }

    private fun createWorkspace(workspaceId: String): String {
        val deployedId = AuthContext.runAsSystem {
            workspaceService.deployWorkspace(
                Workspace.create()
                    .withId(workspaceId)
                    .withName(MLText(workspaceId))
                    .build()
            )
        }
        workspacesToDelete.add(WorkspaceDesc.getRef(deployedId))
        return deployedId
    }

    private fun saveAspect(aspectId: String, prefix: String, vararg attIds: String) {
        val aspectDef = AspectDef.create {
            withId(aspectId)
            withPrefix(prefix)
            withAttributes(attIds.map { attId -> AttributeDef.create { withId(attId) } })
        }
        if (!aspectsToDelete.contains(aspectId)) {
            aspectsToDelete.add(aspectId)
        }
        AuthContext.runAsSystem {
            recordsService.mutate(EntityRef.create(AppName.EMODEL, AspectConstants.ASPECT_SOURCE, ""), aspectDef)
        }
    }

    private fun aspectRef(aspectId: String): EntityRef {
        return EntityRef.create(AppName.EMODEL, AspectConstants.ASPECT_SOURCE, aspectId)
    }

    private fun typeRepoRef(typeId: String): EntityRef {
        return EntityRef.create(AppName.EMODEL, "types-repo", typeId)
    }
}
