package ru.citeck.ecos.model.domain.workspace.utils

import com.google.common.io.BaseEncoding
import com.google.common.primitives.Ints
import kotlin.random.Random

object WorkspaceSystemIdUtils {

    const val USER_WS_SYS_ID_PREFIX = "user__"

    private const val SRC_ID_SIZE_LIMIT = 25
    private val INVALID_CHARS_REGEX = "[^a-zA-Z0-9_-]".toRegex()
    private val ALLOWED_DELIMITERS = setOf('-', '_')
    private val DOUBLE_DELIM_REGEX = "[${ALLOWED_DELIMITERS.joinToString("")}]{2,}".toRegex()

    fun createId(workspaceId: String, checkExisting: (String) -> Boolean = { false }): String {

        var id = workspaceId
        id = id.replace(INVALID_CHARS_REGEX, "_")
        id = id.replace(DOUBLE_DELIM_REGEX, "_")

        id = if (ALLOWED_DELIMITERS.contains(id.lastOrNull())) id.substring(0, id.length - 1) else id
        id = if (ALLOWED_DELIMITERS.contains(id.firstOrNull())) id.substring(1) else id

        if (id.length > SRC_ID_SIZE_LIMIT) {
            id = id.substring(0, SRC_ID_SIZE_LIMIT)
        }
        if (id.isEmpty()) {
            id = BaseEncoding.base32().encode(Ints.toByteArray(Random.nextInt()))
            id = id.lowercase().substringBefore('=')
        }
        if (!checkExisting(id)) {
            return id
        }
        var idx = 1
        val baseId = id
        do {
            val suffix = "_" + idx++
            val overflowSize = (baseId.length + suffix.length) - SRC_ID_SIZE_LIMIT
            id = if (overflowSize > 0) {
                baseId.substring(0, baseId.length - overflowSize) + suffix
            } else {
                baseId + suffix
            }
        } while (checkExisting(id))
        return id
    }
}
