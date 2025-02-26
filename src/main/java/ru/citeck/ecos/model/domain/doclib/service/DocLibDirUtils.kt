package ru.citeck.ecos.model.domain.doclib.service

import com.google.common.primitives.Longs
import org.apache.commons.codec.digest.MurmurHash3
import org.apache.commons.io.output.ByteArrayOutputStream
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.util.*

object DocLibDirUtils {

    fun calculatePathHash(path: List<EntityRef>): String {
        if (path.isEmpty()) {
            return ""
        }
        val bytes = ByteArrayOutputStream(path.size * 50)
        path.forEach { bytes.write(it.toString().toByteArray()) }
        return Base64.getEncoder().encodeToString(Longs.toByteArray(MurmurHash3.hash128x64(bytes.toByteArray())[0]))
    }
}
