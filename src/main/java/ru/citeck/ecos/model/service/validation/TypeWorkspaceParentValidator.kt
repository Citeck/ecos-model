package ru.citeck.ecos.model.service.validation

import ru.citeck.ecos.commons.exception.I18nRuntimeException

/**
 * A type may inherit only from a global type or from a type of its own workspace.
 *
 * A global child of a workspace parent makes a global artifact depend on a workspace: the parent
 * identifier is built through the workspace id mapping, so at startup that lookup lands in the
 * "source is not registered yet" window (COREDEV-550), and afterwards the type splits in two - the
 * row stays global while its resolved definition and records source id inherit the parent's
 * workspace. A parent from a foreign workspace links two workspaces the same way.
 */
object TypeWorkspaceParentValidator {

    const val MSG_GLOBAL_TYPE_WITH_WORKSPACE_PARENT = "ecos-model.type.global-type-with-workspace-parent"
    const val MSG_TYPE_WITH_PARENT_FROM_OTHER_WORKSPACE = "ecos-model.type.type-with-parent-from-other-workspace"

    /** Workspaces are blank for global types. */
    fun validateParentWorkspace(
        typeId: String,
        typeWorkspace: String,
        parentId: String,
        parentWorkspace: String
    ) {
        if (parentWorkspace.isBlank() || typeWorkspace == parentWorkspace) {
            return
        }
        val messageKey = if (typeWorkspace.isBlank()) {
            MSG_GLOBAL_TYPE_WITH_WORKSPACE_PARENT
        } else {
            MSG_TYPE_WITH_PARENT_FROM_OTHER_WORKSPACE
        }
        throw I18nRuntimeException(
            messageKey,
            mapOf(
                "typeId" to typeId,
                "typeWorkspace" to typeWorkspace,
                "parentId" to parentId,
                "parentWorkspace" to parentWorkspace
            )
        )
    }
}
