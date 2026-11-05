package ru.citeck.ecos.model.domain.type

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.model.domain.type.testutils.TypeTestBase
import ru.citeck.ecos.model.lib.workspace.IdInWs
import ru.citeck.ecos.model.type.converter.TypeConverter
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef

class TypeIdentifierValidationTest : TypeTestBase() {

    @Test
    fun colonIsRejectedInNewGlobalAndWorkspaceTypeIds() {
        for (workspace in listOf("", "test-workspace")) {
            for (id in listOf("namespace:type", ":type", "type:", "namespace::type")) {
                val ex = assertThrows<IllegalStateException> {
                    typeService.save(TypeDef.create().withId(id).withWorkspace(workspace).build())
                }
                assertThat(ex.message).contains("Invalid type id: '$id'")
                assertThat(typeService.getByIdOrNull(IdInWs.create(workspace, id))).isNull()
            }
        }
    }

    @Test
    fun recordsCreationCannotBypassColonValidation() {
        for (workspace in listOf("", "test-workspace")) {
            val id = "records:forbidden"
            val ex = assertThrows<Exception> {
                records.create(
                    "emodel/types-repo",
                    ObjectData.create().set("id", id).set("workspace", workspace)
                )
            }
            assertThat(generateSequence<Throwable>(ex) { it.cause }.mapNotNull { it.message }.toList())
                .anyMatch { it.contains("Invalid type id: '$id'") }
            assertThat(typeService.getByIdOrNull(IdInWs.create(workspace, id))).isNull()
        }
    }

    @Test
    fun artifactDeploymentCannotBypassColonValidation() {
        for (workspace in listOf("", "test-workspace")) {
            val id = "artifact:forbidden"
            val ex = assertThrows<IllegalStateException> {
                artifactHandler.deployArtifact(TypeDef.create().withId(id).build(), workspace)
            }
            assertThat(ex.message).contains("Invalid type id: '$id'")
            assertThat(typeService.getByIdOrNull(IdInWs.create(workspace, id))).isNull()
        }
    }

    @Test
    fun existingAllowedIdCharactersAndWorkspaceRemainSupported() {
        for (workspace in listOf("", "test-workspace")) {
            for (id in listOf("plain-type", "under_score", "with.dot", "with/slash", "with\$dollar", "123")) {
                val saved = typeService.save(TypeDef.create().withId(id).withWorkspace(workspace).build())
                assertThat(saved.id).isEqualTo(id)
                assertThat(saved.workspace).isEqualTo(workspace)
            }
        }
    }

    @Test
    fun existingColonTypeCanStillBeUpdatedAndRedeployed() {
        for (workspace in listOf("", "test-workspace")) {
            val id = "legacy:existing"
            // Simulate a row written before the restriction, bypassing service validation.
            val legacy = TypeDef.create().withId(id).withWorkspace(workspace).build()
            typesRepo.save(TypeConverter(typesRepo).toEntity(legacy))
            val updated = typeService.save(legacy.copy().withName(MLText("Updated legacy type")).build())
            assertThat(updated.name).isEqualTo(MLText("Updated legacy type"))
            artifactHandler.deployArtifact(updated, workspace)
            assertThat(typeService.getById(IdInWs.create(workspace, id)).name).isEqualTo(updated.name)
        }
    }

    @Test
    fun cloningToColonIdIsRejected() {
        typeService.save(TypeDef.create().withId("clone-source").build())
        val ex = assertThrows<Exception> {
            records.mutate(
                EntityRef.valueOf("emodel/types-repo@clone-source"),
                ObjectData.create().set("id", "clone:forbidden")
            )
        }
        assertThat(generateSequence<Throwable>(ex) { it.cause }.mapNotNull { it.message }.toList())
            .anyMatch { it.contains("Invalid type id: 'clone:forbidden'") }
        assertThat(typeService.getByIdOrNull(IdInWs.create("clone:forbidden"))).isNull()
        assertThat(typeService.getByIdOrNull(IdInWs.create("clone-source"))).isNotNull()
    }
}
