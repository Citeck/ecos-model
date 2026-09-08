package ru.citeck.ecos.model.service.validation

import ru.citeck.ecos.commons.exception.I18nRuntimeException
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef
import ru.citeck.ecos.model.lib.attributes.dto.computed.ComputedAttStoringType
import ru.citeck.ecos.model.lib.attributes.dto.computed.ComputedAttType
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef

/**
 * Fail-fast guard for attribute ids that can't be stored as a database column.
 *
 * ecos-data names the column of an attribute exactly by the attribute id (for aspect attributes
 * by `prefix:attId`, where a blank prefix falls back to the aspect id). PostgreSQL silently
 * truncates identifiers longer than NAMEDATALEN-1 bytes, after which every write of the type fails
 * with "column ... already exists" and reads of the attribute return nothing. Rejecting the
 * definition here surfaces the mistake at deploy time with a message that names the fix.
 */
object ModelAttColumnNameValidator {

    /**
     * Portable platform limit: the minimum over supported storage backends, today PostgreSQL
     * (NAMEDATALEN - 1 = 63 bytes). emodel can't know the dialect of every application that
     * stores records of a type, so a model has to fit the strictest supported backend.
     */
    const val MAX_COLUMN_NAME_BYTES = 63

    const val MSG_TYPE_ATT_TOO_LONG = "ecos-model.type.attribute-id-too-long"
    const val MSG_ASPECT_ATT_TOO_LONG = "ecos-model.aspect.attribute-id-too-long"

    private const val ARG_TYPE_ID = "typeId"
    private const val ARG_ASPECT_ID = "aspectId"

    /**
     * Mirrors ru.citeck.ecos.data.sql.ecostype.DbEcosModelService.mapAttToColumn (ecos-data):
     * an attribute becomes a column only when its column name matches this pattern, doesn't start
     * with '_' and the attribute is not computed without a storing type. Anything else is never a
     * column, whatever its length. The name rules apply to the column name, not the bare attribute
     * id: for an aspect ecos-data sees the id already prefixed, so `_x` under prefix `p` is the
     * column `p:_x`.
     */
    private val COLUMN_NAME_PATTERN = "[\\w-_:]+".toRegex()

    fun validateTypeAtts(typeId: String, model: TypeModelDef) {
        for (att in model.attributes) {
            validate(att, att.id, MSG_TYPE_ATT_TOO_LONG, ARG_TYPE_ID, typeId)
        }
        for (att in model.systemAttributes) {
            validate(att, att.id, MSG_TYPE_ATT_TOO_LONG, ARG_TYPE_ID, typeId)
        }
    }

    /**
     * @param prefix raw aspect prefix; when blank the aspect id is used, as AspectDef.getAspectInfo does
     */
    fun validateAspectAtts(
        aspectId: String,
        prefix: String,
        attributes: List<AttributeDef>,
        systemAttributes: List<AttributeDef>
    ) {
        val effectivePrefix = prefix.ifBlank { aspectId }
        for (att in attributes) {
            validate(att, "$effectivePrefix:${att.id}", MSG_ASPECT_ATT_TOO_LONG, ARG_ASPECT_ID, aspectId)
        }
        for (att in systemAttributes) {
            validate(att, "$effectivePrefix:${att.id}", MSG_ASPECT_ATT_TOO_LONG, ARG_ASPECT_ID, aspectId)
        }
    }

    fun isStoredAsColumn(att: AttributeDef, columnName: String = att.id): Boolean {
        if (att.id.isBlank() || columnName.startsWith("_") || !COLUMN_NAME_PATTERN.matches(columnName)) {
            return false
        }
        val computed = att.computed
        return computed.type == ComputedAttType.NONE || computed.storingType != ComputedAttStoringType.NONE
    }

    private fun validate(att: AttributeDef, columnName: String, msgKey: String, ownerArg: String, ownerId: String) {
        if (!isStoredAsColumn(att, columnName)) {
            return
        }
        val length = columnName.toByteArray(Charsets.UTF_8).size
        if (length > MAX_COLUMN_NAME_BYTES) {
            throw I18nRuntimeException(
                msgKey,
                mapOf(
                    ownerArg to ownerId,
                    "attribute" to columnName,
                    "length" to length,
                    "limit" to MAX_COLUMN_NAME_BYTES
                )
            )
        }
    }
}
