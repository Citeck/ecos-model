package ru.citeck.ecos.model.type.service.utils

import com.google.common.primitives.Longs
import org.apache.commons.codec.binary.Base32
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import java.util.zip.CRC32

object EModelTypeUtils {

    const val STORAGE_TYPE_EMODEL = "ECOS_MODEL"
    const val STORAGE_TYPE_ALFRESCO = "ALFRESCO"
    const val STORAGE_TYPE_DEFAULT = "DEFAULT"
    const val STORAGE_TYPE_REFERENCE = "REFERENCE"

    private val INVALID_TABLE_SYMBOLS_REGEX = "[^a-z\\d_]+".toRegex()
    private val INVALID_SOURCE_ID_SYMBOLS_REGEX = "[^a-z\\d-]+".toRegex()

    private val CAMEL_REGEX = "(?<=[a-z])[A-Z]".toRegex()

    private val EMODEL_SOURCE_ID_PREFIX = EcosModelApp.NAME + EntityRef.APP_NAME_DELIMITER

    fun getEmodelSourceId(typeDef: TypeDef?): String {
        if (typeDef == null) {
            return ""
        }
        val srcId = typeDef.sourceId
        return if (typeDef.storageType == STORAGE_TYPE_EMODEL) {
            if (srcId.startsWith(EMODEL_SOURCE_ID_PREFIX)) {
                srcId.substring(EMODEL_SOURCE_ID_PREFIX.length)
            } else if (srcId.isNotBlank()) {
                if (srcId.contains("/")) {
                    getEmodelSourceId(typeDef.id)
                } else {
                    srcId
                }
            } else {
                getEmodelSourceId(typeDef.id)
            }
        } else {
            ""
        }
    }

    fun getEmodelSourceId(typeId: String): String {
        return createId(typeId, INVALID_SOURCE_ID_SYMBOLS_REGEX, "-", 42, false)
    }

    fun getEmodelSourceTableId(typeId: String): String {
        return createId(typeId, INVALID_TABLE_SYMBOLS_REGEX, "_", 42, true)
    }

    private fun createId(
        typeId: String,
        invalidSymbolsRegex: Regex,
        delimiter: String,
        maxLen: Int,
        addPrefix: Boolean
    ): String {
        var result = CAMEL_REGEX.replace(typeId) { "_${it.value}" }.lowercase()
        result = result.replace(invalidSymbolsRegex, delimiter)
        if (result.length > maxLen) {
            val crcBeginIdx = maxLen - 8
            val crc = getCrcStr(typeId.substring(crcBeginIdx))
            result = result.substring(0, crcBeginIdx) + delimiter + crc
        }
        val doubleDelim = delimiter.repeat(2)
        while (result.contains(doubleDelim)) {
            result = result.replace(doubleDelim, delimiter)
        }
        if (result.endsWith(delimiter)) {
            result = result.substring(0, result.length - 1)
        }
        if (result.startsWith(delimiter)) {
            result = result.substring(1)
        }
        return if (addPrefix) {
            "t$delimiter$result"
        } else {
            result
        }
    }

    private fun getCrcStr(text: String): String {

        val crc = CRC32()
        crc.update(text.toByteArray())
        val base32 = Base32()
        var result = base32.encodeToString(Longs.toByteArray(crc.value))
            .lowercase()
            .substringBefore('=')

        var idxOfLastLeadingA = -1
        while (idxOfLastLeadingA < result.length - 1 && result[idxOfLastLeadingA + 1] == 'a') {
            idxOfLastLeadingA++
        }
        if (idxOfLastLeadingA > 0) {
            result = result.substring(idxOfLastLeadingA + 1)
        }
        return result
    }
}
