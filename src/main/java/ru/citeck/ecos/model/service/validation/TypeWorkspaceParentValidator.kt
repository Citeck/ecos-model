package ru.citeck.ecos.model.service.validation

import ru.citeck.ecos.commons.exception.I18nRuntimeException

/**
 * A global type may not inherit from a type living in a workspace: the parent identifier is built
 * through the workspace id mapping, so the global type starts depending on a workspace. At startup
 * that lookup lands in the "source is not registered yet" window (COREDEV-550), and afterwards the
 * type splits in two - the row stays global while its resolved definition and records source id
 * inherit the workspace of the parent.
 */
object TypeWorkspaceParentValidator {

    const val MSG_GLOBAL_TYPE_WITH_WORKSPACE_PARENT = "ecos-model.type.global-type-with-workspace-parent"

    /** Workspaces are blank for global types. */
    fun validateParentWorkspace(
        typeId: String,
        typeWorkspace: String,
        parentId: String,
        parentWorkspace: String
    ) {
        if (typeWorkspace.isNotBlank() || parentWorkspace.isBlank()) {
            return
        }
        throw I18nRuntimeException(
            MSG_GLOBAL_TYPE_WITH_WORKSPACE_PARENT,
            mapOf(
                "typeId" to typeId,
                "parentId" to parentId,
                "parentWorkspace" to parentWorkspace
            )
        )
    }
}
