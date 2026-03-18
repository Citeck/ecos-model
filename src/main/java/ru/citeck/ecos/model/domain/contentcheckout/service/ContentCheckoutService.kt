package ru.citeck.ecos.model.domain.contentcheckout.service

import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.time.Instant

@Component
class ContentCheckoutService(
    private val recordsService: RecordsService
) {

    enum class Mode {
        MANUAL,
        EDITOR
    }

    companion object {
        private const val ASPECT_ID = "content-checkout"

        private const val ATT_IS_CHECKED_OUT = "checkout:isCheckedOut?bool"
        private const val ATT_CHECKED_OUT_BY = "checkout:checkedOutBy?localId"
        private const val ATT_CHECKED_OUT_MODE = "checkout:checkedOutMode?str"

        private fun parseMode(value: String): Mode? {
            return Mode.entries.firstOrNull { it.name == value }
        }
    }

    fun checkout(ref: EntityRef, mode: Mode = Mode.MANUAL) {
        validateContentWritePermission(ref)
        val atts = AuthContext.runAsSystem {
            recordsService.getAtts(
                ref,
                mapOf(
                    "isCheckedOut" to ATT_IS_CHECKED_OUT,
                    "checkedOutBy" to ATT_CHECKED_OUT_BY,
                    "checkedOutMode" to ATT_CHECKED_OUT_MODE
                )
            ).getAtts()
        }
        val isCheckedOut = atts["isCheckedOut"].asBoolean()
        val checkedOutBy = atts["checkedOutBy"].asText()
        val checkedOutMode = parseMode(atts["checkedOutMode"].asText())
        val currentUser = AuthContext.getCurrentUser()

        if (isCheckedOut) {
            if (mode == Mode.EDITOR && checkedOutMode == Mode.EDITOR) {
                return
            }
            if (checkedOutBy == currentUser && checkedOutMode == mode) {
                return
            }
            val lockedBy = if (checkedOutMode == Mode.EDITOR) {
                "document editor"
            } else {
                "'$checkedOutBy'"
            }
            error("Document content is already checked out by $lockedBy")
        }

        AuthContext.runAsSystem {
            recordsService.mutate(
                ref,
                mapOf(
                    "att_add__aspects" to ASPECT_ID,
                    "checkout:isCheckedOut" to true,
                    "checkout:checkedOutBy" to EntityRef.create(AppName.EMODEL, "person", currentUser),
                    "checkout:checkedOutTime" to Instant.now(),
                    "checkout:checkedOutMode" to mode.name
                )
            )
        }
    }

    fun cancelCheckout(ref: EntityRef) {
        validateAuthorOrAdmin(ref)
        removeCheckoutAspect(ref)
    }

    fun checkin(
        ref: EntityRef,
        content: EntityRef,
        comment: String = "",
        majorVersion: Boolean = false,
        contentAtt: String = RecordConstants.ATT_CONTENT
    ) {
        if (!isCheckedOut(ref)) {
            error("Document is not checked out. Checkin is not allowed.")
        }
        validateCheckinAllowed(ref)
        val versionDiff = if (majorVersion) "+1.0" else "+0.1"
        AuthContext.runAsSystem {
            recordsService.mutate(
                ref,
                mapOf(
                    "version:version" to versionDiff,
                    "version:comment" to comment,
                    contentAtt to content
                )
            )
        }
        removeCheckoutAspect(ref)
    }

    data class CheckoutState(
        val isCheckedOut: Boolean,
        val checkedOutBy: String,
        val checkedOutMode: Mode?
    )

    fun getCheckoutState(ref: EntityRef): CheckoutState {
        val atts = AuthContext.runAsSystem {
            recordsService.getAtts(
                ref,
                mapOf(
                    "isCheckedOut" to ATT_IS_CHECKED_OUT,
                    "checkedOutBy" to ATT_CHECKED_OUT_BY,
                    "checkedOutMode" to ATT_CHECKED_OUT_MODE
                )
            ).getAtts()
        }
        return CheckoutState(
            isCheckedOut = atts["isCheckedOut"].asBoolean(),
            checkedOutBy = atts["checkedOutBy"].asText(),
            checkedOutMode = parseMode(atts["checkedOutMode"].asText())
        )
    }

    fun isCheckedOut(ref: EntityRef): Boolean {
        return getCheckoutState(ref).isCheckedOut
    }

    fun getCheckedOutBy(ref: EntityRef): String {
        return getCheckoutState(ref).checkedOutBy
    }

    fun getCheckedOutMode(ref: EntityRef): Mode? {
        return getCheckoutState(ref).checkedOutMode
    }

    private fun removeCheckoutAspect(ref: EntityRef) {
        AuthContext.runAsSystem {
            recordsService.mutate(
                ref,
                mapOf(
                    "att_rem__aspects" to ASPECT_ID,
                    "checkout:isCheckedOut" to false,
                    "checkout:checkedOutBy" to "",
                    "checkout:checkedOutTime" to null,
                    "checkout:checkedOutMode" to ""
                )
            )
        }
    }

    private fun validateContentWritePermission(ref: EntityRef) {
        if (AuthContext.isRunAsSystem()) {
            return
        }
        val perms = recordsService.getAtts(
            ref,
            mapOf(
                "hasWrite" to "permissions._has.Write?bool!true",
                "isContentProtected" to "${RecordConstants.ATT_EDGE}.${RecordConstants.ATT_CONTENT}.protected?bool!"
            )
        ).getAtts()
        if (!perms["hasWrite"].asBoolean() || perms["isContentProtected"].asBoolean()) {
            error("Permission denied: current user cannot modify content of this document")
        }
    }

    private fun validateCheckinAllowed(ref: EntityRef) {
        if (AuthContext.isRunAsSystem()) {
            return
        }
        validateModeAndAuthor(ref, "can check in")
    }

    private fun validateAuthorOrAdmin(ref: EntityRef) {
        if (AuthContext.isRunAsSystemOrAdmin()) {
            return
        }
        if (!isCheckedOut(ref)) {
            return
        }
        validateModeAndAuthor(ref, "or an admin can perform this action")
    }

    private fun validateModeAndAuthor(ref: EntityRef, errorSuffix: String) {
        val state = getCheckoutState(ref)
        if (state.checkedOutMode == Mode.EDITOR) {
            validateContentWritePermission(ref)
            return
        }
        val currentUser = AuthContext.getCurrentUser()
        if (state.checkedOutBy != currentUser) {
            error("Only the user who checked out the document content ('${state.checkedOutBy}') $errorSuffix")
        }
    }
}
