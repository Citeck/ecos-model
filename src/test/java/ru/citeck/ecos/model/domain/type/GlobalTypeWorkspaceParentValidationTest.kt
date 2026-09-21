package ru.citeck.ecos.model.domain.type

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.exception.I18nRuntimeException
import ru.citeck.ecos.model.domain.type.testutils.TypeTestBase
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.model.lib.workspace.IdInWs
import ru.citeck.ecos.model.service.validation.TypeWorkspaceParentValidator
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef

/**
 * A global type may not inherit from a type living in a workspace (COREDEV-550),
 * see [TypeWorkspaceParentValidator].
 */
class GlobalTypeWorkspaceParentValidationTest : TypeTestBase() {

    private fun saveType(id: String, workspace: String = "", parent: String = "base"): TypeDef {
        return typeService.save(
            TypeDef.create()
                .withId(id)
                .withWorkspace(workspace)
                .withParentRef(ModelUtils.getTypeRef(parent))
                .build()
        )
    }

    @Test
    fun globalTypeWithParentInWorkspaceIsRejected() {

        saveType("parent-in-ws", workspace = "ws1")

        val ex = assertThrows<Exception> {
            saveType("global-child", parent = "parent-in-ws")
        }

        val i18n = i18nCause(ex)
        assertThat(i18n.messageKey).isEqualTo("ecos-model.type.global-type-with-workspace-parent")
        assertThat(i18n.messageArgs).containsEntry("typeId", "global-child")
        assertThat(i18n.messageArgs).containsEntry("parentId", "parent-in-ws")
        assertThat(i18n.messageArgs).containsEntry("parentWorkspace", "ws1")
        assertThat(typeService.getByIdOrNull(IdInWs.create("global-child"))).isNull()
    }

    @Test
    fun globalTypeWithParentInWorkspaceIsRejectedThroughRecords() {

        saveType("parent-in-ws", workspace = "ws1")

        val ex = assertThrows<Exception> {
            records.create(
                "emodel/types-repo",
                ObjectData.create()
                    .set("id", "records-child")
                    .set("parent", ModelUtils.getTypeRef("parent-in-ws").toString())
            )
        }

        assertThat(i18nCause(ex).messageKey).isEqualTo("ecos-model.type.global-type-with-workspace-parent")
        assertThat(typeService.getByIdOrNull(IdInWs.create("records-child"))).isNull()
    }

    @Test
    fun movingGlobalTypeUnderParentInWorkspaceIsRejected() {

        saveType("parent-in-ws", workspace = "ws1")
        saveType("movable-child")

        val ex = assertThrows<Exception> {
            saveType("movable-child", parent = "parent-in-ws")
        }

        assertThat(i18nCause(ex).messageKey).isEqualTo("ecos-model.type.global-type-with-workspace-parent")
        assertThat(typeService.getById(IdInWs.create("movable-child")).parentRef.getLocalId()).isEqualTo("base")
    }

    @Test
    fun typeOfTheSameWorkspaceWithParentInWorkspaceIsAllowed() {

        saveType("parent-in-ws", workspace = "ws1")

        val saved = saveType("child-in-ws", workspace = "ws1", parent = "parent-in-ws")

        assertThat(saved.parentRef.getLocalId()).isEqualTo("parent-in-ws")
    }

    @Test
    fun typeInWorkspaceWithGlobalParentIsAllowed() {

        val saved = saveType("child-in-ws-global-parent", workspace = "ws1")

        assertThat(saved.parentRef.getLocalId()).isEqualTo("base")
    }

    @Test
    fun globalTypeWithGlobalParentIsAllowed() {

        val saved = saveType("global-child-global-parent")

        assertThat(saved.parentRef.getLocalId()).isEqualTo("base")
    }

    private fun i18nCause(ex: Throwable): I18nRuntimeException {
        var cur: Throwable? = ex
        while (cur != null) {
            if (cur is I18nRuntimeException) return cur
            cur = cur.cause
        }
        throw AssertionError("I18nRuntimeException not found in cause chain of $ex", ex)
    }
}
