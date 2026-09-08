package ru.citeck.ecos.model.service.validation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import ru.citeck.ecos.commons.exception.I18nRuntimeException
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef
import ru.citeck.ecos.model.lib.attributes.dto.computed.ComputedAttDef
import ru.citeck.ecos.model.lib.attributes.dto.computed.ComputedAttStoringType
import ru.citeck.ecos.model.lib.attributes.dto.computed.ComputedAttType
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef
import ru.citeck.ecos.model.service.validation.ModelAttColumnNameValidator.MAX_COLUMN_NAME_BYTES
import ru.citeck.ecos.model.service.validation.ModelAttColumnNameValidator.MSG_ASPECT_ATT_TOO_LONG
import ru.citeck.ecos.model.service.validation.ModelAttColumnNameValidator.MSG_TYPE_ATT_TOO_LONG

class ModelAttColumnNameValidatorTest {

    private fun att(id: String): AttributeDef = AttributeDef.create { withId(id) }

    private fun model(atts: List<AttributeDef>, systemAtts: List<AttributeDef> = emptyList()): TypeModelDef {
        return TypeModelDef.create {
            withAttributes(atts)
            withSystemAttributes(systemAtts)
        }
    }

    private fun computedAtt(id: String, storingType: ComputedAttStoringType): AttributeDef {
        return AttributeDef.create {
            withId(id)
            withComputed(
                ComputedAttDef.create {
                    withType(ComputedAttType.SCRIPT)
                    withStoringType(storingType)
                }
            )
        }
    }

    @Test
    fun typeAttAtLimitPasses() {
        val id = "a".repeat(MAX_COLUMN_NAME_BYTES)
        assertDoesNotThrow { ModelAttColumnNameValidator.validateTypeAtts("t", model(listOf(att(id)))) }
    }

    @Test
    fun typeAttOverLimitFails() {
        val id = "a".repeat(MAX_COLUMN_NAME_BYTES + 1)
        val ex = assertThrows<I18nRuntimeException> {
            ModelAttColumnNameValidator.validateTypeAtts("t", model(listOf(att(id))))
        }
        assertThat(ex.messageKey).isEqualTo(MSG_TYPE_ATT_TOO_LONG)
        assertThat(ex.messageArgs).containsEntry("typeId", "t")
        assertThat(ex.messageArgs).containsEntry("attribute", id)
        assertThat(ex.messageArgs).containsEntry("length", MAX_COLUMN_NAME_BYTES + 1)
        assertThat(ex.messageArgs).containsEntry("limit", MAX_COLUMN_NAME_BYTES)
    }

    @Test
    fun typeSystemAttOverLimitFails() {
        val id = "s".repeat(MAX_COLUMN_NAME_BYTES + 1)
        val ex = assertThrows<I18nRuntimeException> {
            ModelAttColumnNameValidator.validateTypeAtts("t", model(emptyList(), listOf(att(id))))
        }
        assertThat(ex.messageArgs).containsEntry("attribute", id)
    }

    @Test
    fun aspectWithoutPrefixUsesAspectIdAsPrefix() {
        val aspectId = "a".repeat(20)
        val attId = "b".repeat(43)
        val ex = assertThrows<I18nRuntimeException> {
            ModelAttColumnNameValidator.validateAspectAtts(aspectId, "", listOf(att(attId)), emptyList())
        }
        assertThat(ex.messageKey).isEqualTo(MSG_ASPECT_ATT_TOO_LONG)
        assertThat(ex.messageArgs).containsEntry("aspectId", aspectId)
        assertThat(ex.messageArgs).containsEntry("attribute", "$aspectId:$attId")
        assertThat(ex.messageArgs).containsEntry("length", MAX_COLUMN_NAME_BYTES + 1)
        assertThat(ex.messageArgs).containsEntry("limit", MAX_COLUMN_NAME_BYTES)
    }

    @Test
    fun shortPrefixMakesTheSameAttFit() {
        val aspectId = "a".repeat(20)
        val attId = "b".repeat(43)
        assertDoesNotThrow {
            ModelAttColumnNameValidator.validateAspectAtts(aspectId, "p", listOf(att(attId)), emptyList())
        }
    }

    @Test
    fun aspectPrefixBoundary() {
        val fits = "b".repeat(MAX_COLUMN_NAME_BYTES - 2)
        val overflows = "b".repeat(MAX_COLUMN_NAME_BYTES - 1)
        assertDoesNotThrow {
            ModelAttColumnNameValidator.validateAspectAtts("asp", "p", listOf(att(fits)), emptyList())
        }
        val ex = assertThrows<I18nRuntimeException> {
            ModelAttColumnNameValidator.validateAspectAtts("asp", "p", listOf(att(overflows)), emptyList())
        }
        assertThat(ex.messageArgs).containsEntry("attribute", "p:$overflows")
    }

    @Test
    fun aspectSystemAttsAreChecked() {
        val overflows = "b".repeat(MAX_COLUMN_NAME_BYTES - 1)
        val ex = assertThrows<I18nRuntimeException> {
            ModelAttColumnNameValidator.validateAspectAtts("asp", "p", emptyList(), listOf(att(overflows)))
        }
        assertThat(ex.messageArgs).containsEntry("attribute", "p:$overflows")
    }

    @Test
    fun attsThatNeverBecomeColumnsAreSkipped() {
        val cyrillic = "ф".repeat(40)
        assertThat(cyrillic.toByteArray(Charsets.UTF_8).size).isGreaterThan(MAX_COLUMN_NAME_BYTES)
        val system = "_" + "x".repeat(70)
        val notStored = computedAtt("c".repeat(70), ComputedAttStoringType.NONE)
        assertDoesNotThrow {
            ModelAttColumnNameValidator.validateTypeAtts("t", model(listOf(att(cyrillic), att(system), notStored)))
        }
        assertThat(ModelAttColumnNameValidator.isStoredAsColumn(att(cyrillic))).isFalse()
        assertThat(ModelAttColumnNameValidator.isStoredAsColumn(att(system))).isFalse()
        assertThat(ModelAttColumnNameValidator.isStoredAsColumn(notStored)).isFalse()
    }

    @Test
    fun underscoreAttOfAspectIsAColumnUnderItsPrefix() {
        // ecos-data checks the prefixed id: "p:_x" doesn't start with '_', so it is a column
        val system = "_" + "x".repeat(70)
        assertThat(ModelAttColumnNameValidator.isStoredAsColumn(att(system), "p:$system")).isTrue()
        val ex = assertThrows<I18nRuntimeException> {
            ModelAttColumnNameValidator.validateAspectAtts("a", "p", listOf(att(system)), emptyList())
        }
        assertThat(ex.messageArgs).containsEntry("attribute", "p:$system")
    }

    @Test
    fun storedComputedAttIsChecked() {
        val stored = computedAtt("c".repeat(70), ComputedAttStoringType.ON_CREATE)
        assertThat(ModelAttColumnNameValidator.isStoredAsColumn(stored)).isTrue()
        val ex = assertThrows<I18nRuntimeException> {
            ModelAttColumnNameValidator.validateTypeAtts("t", model(listOf(stored)))
        }
        assertThat(ex.messageArgs).containsEntry("length", 70)
    }
}
