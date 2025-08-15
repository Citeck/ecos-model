package ru.citeck.ecos.model.domain.workspace.utils

import com.google.common.io.BaseEncoding
import com.google.common.primitives.Longs
import org.apache.commons.codec.binary.Base32
import kotlin.random.Random

object WorkspaceSystemIdUtils {

    private const val SRC_ID_SIZE_LIMIT = 20
    private val INVALID_CHARS_REGEX = "[^a-zA-Z0-9_-]".toRegex()

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
        var idx = 1
        val baseId = id
        while (checkExisting(id)) {
            id = baseId + "_" + idx++
        }
        return id
    }
}
