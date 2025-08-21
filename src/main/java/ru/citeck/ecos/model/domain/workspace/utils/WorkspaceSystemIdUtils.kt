package ru.citeck.ecos.model.domain.workspace.utils

import com.google.common.io.BaseEncoding
import com.google.common.primitives.Longs
import kotlin.random.Random

object WorkspaceSystemIdUtils {

    private const val SYS_ID_PREFIX_PREFIX = "ws_"
    private const val SYS_ID_PREFIX_DELIM = "/"

    private const val SRC_ID_SIZE_LIMIT = 25
    private val INVALID_CHARS_REGEX = "[^a-zA-Z0-9_-]".toRegex()

    fun removeWsPrefixFromId(id: String): String {
        if (!id.startsWith(SYS_ID_PREFIX_PREFIX)) {
            return id
        }
        return id.substringAfterLast(SYS_ID_PREFIX_DELIM)
    }

    fun addWsPrefixToId(localId: String, wsSysId: String): String {
        return SYS_ID_PREFIX_PREFIX + wsSysId  + SYS_ID_PREFIX_DELIM + localId
    }

    fun createId(workspaceId: String, checkExisting: (String) -> Boolean): String {

        var id = workspaceId
        if (id.length > SRC_ID_SIZE_LIMIT) {
            id = id.substring(0, SRC_ID_SIZE_LIMIT)
        }
        id = id.replace(INVALID_CHARS_REGEX, "_")
        while (id.isNotEmpty() && !id.last().isLetterOrDigit()) {
            id = id.substring(0, id.length - 1)
        }
        if (id.isEmpty()) {
            id = BaseEncoding.base32().encode(Longs.toByteArray(Random.nextLong()))
            id = id.lowercase().substringBefore('=')
        }
        if (!checkExisting(id)) {
            return id
        }
        var idx = 1
        val baseId = id
        do {
            val suffix = "_" + idx++
            id = baseId.substring(0, baseId.length - suffix.length) + suffix
        } while (checkExisting(id))
        return id
    }
}
