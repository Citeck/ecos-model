package ru.citeck.ecos.model.domain.treesearch

import com.google.common.primitives.Longs
import org.apache.commons.codec.digest.MurmurHash3
import org.apache.commons.io.output.ByteArrayOutputStream
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.util.*

object TreeSearchDesc {

    const val ASPECT_ID = "tree-search"
    const val ASPECT_PREFIX = ASPECT_ID

    const val ATT_PATH = "$ASPECT_PREFIX:path"
    const val ATT_PATH_HASH = "$ASPECT_PREFIX:pathHash"
    const val ATT_PARENT_PATH_HASH = "$ASPECT_PREFIX:parentPathHash"

    const val ATT_LEAF_ASSOCS_TO_UPDATE = "$ASPECT_PREFIX:leafAssocsToUpdate"

    fun calculatePathHash(path: List<EntityRef>): String {
        if (path.isEmpty()) {
            return ""
        }
        val bytes = ByteArrayOutputStream(path.size * 50)
        path.forEach { bytes.write(it.toString().toByteArray()) }
        return Base64.getEncoder().encodeToString(Longs.toByteArray(MurmurHash3.hash128x64(bytes.toByteArray())[0]))
    }
}
