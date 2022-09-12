package ru.citeck.ecos.model.type.service.utils

import com.google.common.primitives.Longs
import org.apache.commons.codec.binary.Base32
import java.util.zip.CRC32

object EModelTypeUtils {

    const val STORAGE_TYPE_EMODEL = "ECOS_MODEL"
    const val STORAGE_TYPE_ALFRESCO = "ALFRESCO"
    const val STORAGE_TYPE_DEFAULT = "DEFAULT"
    const val STORAGE_TYPE_REFERENCE = "REFERENCE"

    private val INVALID_TABLE_SYMBOLS_REGEX = "[^a-z\\d_]+".toRegex()
    private val INVALID_SOURCE_ID_SYMBOLS_REGEX = "[^a-z\\d-]+".toRegex()

    private val CAMEL_REGEX = "(?<=[a-z])[A-Z]".toRegex()

    fun getEmodelSourceId(typeId: String): String {
        return createId(typeId, INVALID_SOURCE_ID_SYMBOLS_REGEX, "-", 42)
    }

    fun getEmodelSourceTableId(typeId: String): String {
        return createId(typeId, INVALID_TABLE_SYMBOLS_REGEX, "_", 42)
    }

    private fun createId(typeId: String, invalidSymbolsRegex: Regex, delimiter: String, maxLen: Int): String {
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
        return "t$delimiter$result"
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
