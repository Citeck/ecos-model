package ru.citeck.ecos.model.type.service.utils

import com.google.common.primitives.Longs
import org.apache.commons.codec.binary.Base32
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.model.lib.workspace.IdInWs
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import java.util.zip.CRC32

@Component
class EModelTypeUtils {

    companion object {
        val ABSTRACT_TYPES = setOf(
            "base",
            "user-base",
            "case",
            "data-list",
            "authority"
        )

        const val STORAGE_TYPE_EMODEL = "ECOS_MODEL"
        const val STORAGE_TYPE_ALFRESCO = "ALFRESCO"
        const val STORAGE_TYPE_DEFAULT = "DEFAULT"
        const val STORAGE_TYPE_REFERENCE = "REFERENCE"

        private val INVALID_TABLE_SYMBOLS_REGEX = "[^a-z\\d:_]+".toRegex()
        private val INVALID_SOURCE_ID_SYMBOLS_REGEX = "[^a-z\\d:-]+".toRegex()

        private val CAMEL_REGEX = "(?<=[a-z])[A-Z]".toRegex()

        private val EMODEL_SOURCE_ID_PREFIX = EcosModelApp.NAME + EntityRef.APP_NAME_DELIMITER
    }

    @Autowired
    lateinit var workspaceService: WorkspaceService

    fun getEmodelSourceId(typeDef: TypeDef?): String {
        if (typeDef == null) {
            return ""
        }
        return getEmodelSourceId(typeDef.id, typeDef.workspace, typeDef.storageType, typeDef.sourceId)
    }

    fun getEmodelSourceId(typeId: String, workspace: String, storageType: String, sourceId: String): String {
        if (ABSTRACT_TYPES.contains(typeId)) {
            return ""
        }
        return if (storageType == STORAGE_TYPE_EMODEL) {
            if (sourceId.startsWith(EMODEL_SOURCE_ID_PREFIX)) {
                sourceId.substring(EMODEL_SOURCE_ID_PREFIX.length)
            } else if (sourceId.isNotBlank()) {
                if (sourceId.contains("/")) {
                    getEmodelSourceId(typeId, workspace)
                } else {
                    sourceId
                }
            } else {
                getEmodelSourceId(typeId, workspace)
            }
        } else {
            ""
        }
    }

    fun getEmodelSourceId(typeId: String, workspace: String): String {
        return createId(
            typeId = typeId,
            workspace = workspace,
            invalidSymbolsRegex = INVALID_SOURCE_ID_SYMBOLS_REGEX,
            delimiter = "-",
            maxLen = 42,
            addPrefix = false,
            replaceWsDelim = false
        )
    }

    fun getEmodelSourceTableId(typeId: String, workspace: String): String {
        return createId(
            typeId = typeId,
            workspace = workspace,
            invalidSymbolsRegex = INVALID_TABLE_SYMBOLS_REGEX,
            delimiter = "_",
            maxLen = 42,
            addPrefix = true,
            replaceWsDelim = true
        )
    }

    private fun createId(
        typeId: String,
        workspace: String,
        invalidSymbolsRegex: Regex,
        delimiter: String,
        maxLen: Int,
        addPrefix: Boolean,
        replaceWsDelim: Boolean
    ): String {
        val scopedTypeId = if (!typeId.contains(IdInWs.WS_DELIM)) {
            workspaceService.addWsPrefixToId(typeId, workspace)
        } else {
            typeId
        }
        var result = CAMEL_REGEX.replace(scopedTypeId) { "_${it.value}" }.lowercase()
        result = result.replace(invalidSymbolsRegex, delimiter)
        if (result.length > maxLen) {
            val crcBeginIdx = maxLen - 8
            val crc = getCrcStr(scopedTypeId.substring(crcBeginIdx))
            result = result.substring(0, crcBeginIdx) + delimiter + crc
        }
        val doubleDelim = delimiter.repeat(2)
        while (result.contains(doubleDelim)) {
            result = result.replace(doubleDelim, delimiter)
        }
        if (replaceWsDelim) {
            result = result.replace(IdInWs.WS_DELIM, doubleDelim)
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
