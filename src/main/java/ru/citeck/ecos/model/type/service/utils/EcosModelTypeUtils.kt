package ru.citeck.ecos.model.type.service.utils

import com.google.common.primitives.Longs
import org.apache.commons.codec.binary.Base32
import java.util.zip.CRC32

object EcosModelTypeUtils {

    const val SOURCE_TYPE_EMODEL = "ECOS_MODEL"
    const val SOURCE_TYPE_ALFRESCO = "ALFRESCO"
    const val SOURCE_TYPE_CUSTOM_ID = "CUSTOM_ID"
    const val SOURCE_TYPE_INHERIT = "INHERIT"

    const val DEFAULT_SOURCE_TYPE = SOURCE_TYPE_CUSTOM_ID

    fun generateEmodelSourceId(typeId: String): String {

        val crc = CRC32()
        crc.update(typeId.toByteArray())
        val base32 = Base32()
        var result = base32.encodeToString(Longs.toByteArray(crc.value))
            .lowercase()
            .substringBefore('=')

        var idxOfLastLeadingA = -1
        while (idxOfLastLeadingA < result.length - 1 && result[idxOfLastLeadingA + 1] == 'a') {
            idxOfLastLeadingA++
        }

        if (idxOfLastLeadingA > 0) {
            result = result.substring(idxOfLastLeadingA)
        }

        return "t-$result"
    }

    fun emodelSourceTableId(typeId: String): String {
        return "t_" + typeId.lowercase().replace("[^a-z-_]".toRegex(), "_")
    }
}
