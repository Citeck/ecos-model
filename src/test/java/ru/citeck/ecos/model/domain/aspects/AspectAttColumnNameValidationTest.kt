package ru.citeck.ecos.model.domain.aspects

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.commons.exception.I18nRuntimeException
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.model.lib.aspect.constants.AspectConstants
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef
import ru.citeck.ecos.model.service.validation.ModelAttColumnNameValidator.MAX_COLUMN_NAME_BYTES
import ru.citeck.ecos.model.service.validation.ModelAttColumnNameValidator.MSG_ASPECT_ATT_TOO_LONG
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.aspect.dto.AspectDef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension

/**
 * The aspect save path (AspectsRecordsDao behind the "aspect" source, the same one the artifact
 * handler and the UI write to) rejects attributes whose column name "prefix:attId" can't be stored.
 */
@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosModelApp::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AspectAttColumnNameValidationTest {

    @Autowired
    private lateinit var recordsService: RecordsService

    private val aspectsToDelete = mutableListOf<String>()

    @AfterAll
    fun tearDown() {
        AuthContext.runAsSystem {
            aspectsToDelete.forEach { id ->
                if (exists(id)) {
                    recordsService.delete(aspectRef(id))
                }
            }
        }
    }

    @Test
    fun aspectWithoutPrefixUsesItsIdAsPrefix() {
        val aspectId = "col-len-no-prefix"
        val attId = "b".repeat(MAX_COLUMN_NAME_BYTES - aspectId.length)

        val ex = assertThrows<Exception> {
            saveAspect(aspect(aspectId, "", listOf(attId)))
        }

        val i18n = i18nCause(ex)
        assertThat(i18n.messageKey).isEqualTo(MSG_ASPECT_ATT_TOO_LONG)
        assertThat(i18n.messageArgs).containsEntry("aspectId", aspectId)
        assertThat(i18n.messageArgs).containsEntry("attribute", "$aspectId:$attId")
        assertThat(i18n.messageArgs).containsEntry("length", MAX_COLUMN_NAME_BYTES + 1)
        assertThat(i18n.messageArgs).containsEntry("limit", MAX_COLUMN_NAME_BYTES)
        assertThat(exists(aspectId)).isFalse()
    }

    @Test
    fun shortPrefixMakesTheSameAttFit() {
        val aspectId = "col-len-short-prefix"
        val attId = "b".repeat(MAX_COLUMN_NAME_BYTES - aspectId.length)

        saveAspect(aspect(aspectId, "cls", listOf(attId)))

        assertThat(attIds(aspectId, "attributes")).containsExactly(attId)
    }

    @Test
    fun prefixBoundary() {
        val prefix = "clb"
        val fits = "b".repeat(MAX_COLUMN_NAME_BYTES - prefix.length - 1)
        val overflows = "b".repeat(MAX_COLUMN_NAME_BYTES - prefix.length)

        saveAspect(aspect("col-len-fits", prefix, listOf(fits)))
        assertThat(attIds("col-len-fits", "attributes")).containsExactly(fits)

        val ex = assertThrows<Exception> {
            saveAspect(aspect("col-len-overflows", "clo", listOf(overflows)))
        }
        assertThat(i18nCause(ex).messageArgs).containsEntry("attribute", "clo:$overflows")
        assertThat(exists("col-len-overflows")).isFalse()
    }

    @Test
    fun systemAttsAreChecked() {
        val prefix = "clsys"
        val overflows = "b".repeat(MAX_COLUMN_NAME_BYTES - prefix.length)

        val ex = assertThrows<Exception> {
            saveAspect(aspect("col-len-system", prefix, emptyList(), listOf(overflows)))
        }
        assertThat(i18nCause(ex).messageArgs).containsEntry("attribute", "$prefix:$overflows")
        assertThat(exists("col-len-system")).isFalse()
    }

    @Test
    fun updateAddingTooLongAttIsRejected() {
        val aspectId = "col-len-update"
        val prefix = "clu"
        val overflows = "b".repeat(MAX_COLUMN_NAME_BYTES - prefix.length)
        saveAspect(aspect(aspectId, prefix, listOf("shortAtt")))

        val ex = assertThrows<Exception> {
            saveAspect(aspect(aspectId, prefix, listOf("shortAtt", overflows)))
        }

        assertThat(i18nCause(ex).messageArgs).containsEntry("attribute", "$prefix:$overflows")
        assertThat(attIds(aspectId, "attributes")).containsExactly("shortAtt")
    }

    @Test
    fun prefixOnlyMutationIsValidatedAgainstStoredAtts() {
        val aspectId = "col-len-prefix-only"
        val attId = "b".repeat(MAX_COLUMN_NAME_BYTES - "clp".length - 1)
        saveAspect(aspect(aspectId, "clp", listOf(attId)))

        val ex = assertThrows<Exception> {
            AuthContext.runAsSystem {
                recordsService.mutate(aspectRef(aspectId), mapOf("prefix" to "clpx"))
            }
        }

        assertThat(i18nCause(ex).messageArgs).containsEntry("attribute", "clpx:$attId")
        val storedPrefix = AuthContext.runAsSystem { recordsService.getAtt(aspectRef(aspectId), "prefix").asText() }
        assertThat(storedPrefix).isEqualTo("clp")
    }

    @Test
    fun attsOnlyMutationUsesStoredShortPrefix() {
        // the attribute fits under the stored prefix and would overflow under the aspect id
        val aspectId = "col-len-stored-short-prefix-with-a-long-id"
        val prefix = "css"
        val attId = "b".repeat(MAX_COLUMN_NAME_BYTES - prefix.length - 1)
        saveAspect(aspect(aspectId, prefix, listOf(attId)))
        val another = "c".repeat(MAX_COLUMN_NAME_BYTES - prefix.length - 1)

        AuthContext.runAsSystem {
            recordsService.mutate(aspectRef(aspectId), mapOf("attributes" to listOf(mapOf("id" to another))))
        }

        assertThat(attIds(aspectId, "attributes")).containsExactly(another)
    }

    @Test
    fun attsOnlyMutationUsesStoredLongPrefix() {
        // the attribute fits under the aspect id and overflows under the stored prefix
        val aspectId = "clsl"
        val prefix = "col-len-stored-long-prefix"
        saveAspect(aspect(aspectId, prefix, listOf("shortAtt")))
        val overflows = "b".repeat(MAX_COLUMN_NAME_BYTES - prefix.length)

        val ex = assertThrows<Exception> {
            AuthContext.runAsSystem {
                recordsService.mutate(
                    aspectRef(aspectId),
                    mapOf("attributes" to listOf(mapOf("id" to "shortAtt"), mapOf("id" to overflows)))
                )
            }
        }

        assertThat(i18nCause(ex).messageArgs).containsEntry("attribute", "$prefix:$overflows")
        assertThat(attIds(aspectId, "attributes")).containsExactly("shortAtt")
    }

    @Test
    fun messageIsLocalized() {
        val aspectId = "col-len-i18n"
        val attId = "b".repeat(MAX_COLUMN_NAME_BYTES - aspectId.length)

        val ex = assertThrows<Exception> {
            saveAspect(aspect(aspectId, "", listOf(attId)))
        }

        val i18n = i18nCause(ex)
        val ru = I18nContext.getMessage(i18n.messageKey, I18nContext.RUSSIAN, i18n.messageArgs)
        assertThat(ru).contains("Сократите идентификатор атрибута")
        assertThat(ru).contains("$aspectId:$attId")
        val en = I18nContext.getMessage(i18n.messageKey, I18nContext.ENGLISH, i18n.messageArgs)
        assertThat(en).contains("Shorten the attribute id")
        assertThat(en).contains("${MAX_COLUMN_NAME_BYTES + 1} bytes")
    }

    private fun aspect(
        id: String,
        prefix: String,
        attIds: List<String>,
        systemAttIds: List<String> = emptyList()
    ): AspectDef {
        return AspectDef.create {
            withId(id)
            withPrefix(prefix)
            withAttributes(attIds.map { attId -> AttributeDef.create { withId(attId) } })
            withSystemAttributes(systemAttIds.map { attId -> AttributeDef.create { withId(attId) } })
        }
    }

    private fun saveAspect(def: AspectDef): EntityRef {
        aspectsToDelete.add(def.id)
        return AuthContext.runAsSystem {
            recordsService.mutate(EntityRef.create(AppName.EMODEL, AspectConstants.ASPECT_SOURCE, ""), def)
        }
    }

    private fun aspectRef(id: String): EntityRef {
        return EntityRef.create(AppName.EMODEL, AspectConstants.ASPECT_SOURCE, id)
    }

    private fun exists(id: String): Boolean {
        // "id" of a missing record resolves to the ref itself, so ask the DAO explicitly
        return !AuthContext.runAsSystem {
            recordsService.getAtt(aspectRef(id), RecordConstants.ATT_NOT_EXISTS + "?bool").asBoolean()
        }
    }

    private fun attIds(aspectId: String, listAtt: String): List<String> {
        return AuthContext.runAsSystem {
            recordsService.getAtt(aspectRef(aspectId), "$listAtt[]?json")
                .asList(AttributeDef::class.java)
                .map { it.id }
        }
    }

    private fun i18nCause(ex: Throwable): I18nRuntimeException {
        var cur: Throwable? = ex
        while (cur != null) {
            if (cur is I18nRuntimeException) return cur
            cur = cur.cause
        }
        throw AssertionError("I18nRuntimeException not found in cause chain of $ex", ex)
    }
}
