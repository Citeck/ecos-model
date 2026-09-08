package ru.citeck.ecos.model.domain.type

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.exception.I18nRuntimeException
import ru.citeck.ecos.model.domain.type.testutils.TypeTestBase
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef
import ru.citeck.ecos.model.lib.workspace.IdInWs
import ru.citeck.ecos.model.service.validation.ModelAttColumnNameValidator.MAX_COLUMN_NAME_BYTES
import ru.citeck.ecos.model.service.validation.ModelAttColumnNameValidator.MSG_TYPE_ATT_TOO_LONG
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef

/**
 * A type whose attribute id can't be stored as a column is rejected on every save path that
 * reaches TypesServiceImpl.saveTypeDefImpl: records API, service, artifact deploy.
 */
class TypeAttColumnNameValidationTest : TypeTestBase() {

    private val fits = "a".repeat(MAX_COLUMN_NAME_BYTES)
    private val overflows = "a".repeat(MAX_COLUMN_NAME_BYTES + 1)

    private fun model(vararg attIds: String): TypeModelDef {
        return TypeModelDef.create {
            withAttributes(attIds.map { id -> AttributeDef.create { withId(id) } })
        }
    }

    private fun modelJson(vararg attIds: String): ObjectData {
        return ObjectData.create().set("attributes", attIds.map { ObjectData.create().set("id", it) })
    }

    @Test
    fun typeWithAttAtLimitIsCreated() {
        records.create("emodel/types-repo", ObjectData.create().set("id", "t-ok").set("model", modelJson(fits)))

        val saved = typeService.getById(IdInWs.create("t-ok"))
        assertThat(saved.model.attributes.map { it.id }).containsExactly(fits)
    }

    @Test
    fun typeWithTooLongAttIsRejectedThroughRecords() {
        val ex = assertThrows<Exception> {
            records.create("emodel/types-repo", ObjectData.create().set("id", "t-bad").set("model", modelJson(overflows)))
        }

        val i18n = i18nCause(ex)
        assertThat(i18n.messageKey).isEqualTo(MSG_TYPE_ATT_TOO_LONG)
        assertThat(i18n.messageArgs).containsEntry("typeId", "t-bad")
        assertThat(i18n.messageArgs).containsEntry("attribute", overflows)
        assertThat(i18n.messageArgs).containsEntry("length", MAX_COLUMN_NAME_BYTES + 1)
        assertThat(i18n.messageArgs).containsEntry("limit", MAX_COLUMN_NAME_BYTES)
        assertThat(typeService.getByIdOrNull(IdInWs.create("t-bad"))).isNull()
    }

    @Test
    fun updateOfExistingTypeWithTooLongAttIsRejected() {
        val existing = typeService.save(
            TypeDef.create {
                withId("t-upd")
                withModel(model("shortAtt"))
            }
        )

        val ex = assertThrows<Exception> {
            typeService.save(existing.copy { withModel(model("shortAtt", overflows)) })
        }

        assertThat(i18nCause(ex).messageArgs).containsEntry("attribute", overflows)
        val stored = typeService.getById(IdInWs.create("t-upd"))
        assertThat(stored.model.attributes.map { it.id }).containsExactly("shortAtt")
    }

    @Test
    fun systemAttIsChecked() {
        val ex = assertThrows<Exception> {
            typeService.save(
                TypeDef.create {
                    withId("t-sys")
                    withModel(
                        TypeModelDef.create {
                            withSystemAttributes(listOf(AttributeDef.create { withId(overflows) }))
                        }
                    )
                }
            )
        }
        assertThat(i18nCause(ex).messageArgs).containsEntry("attribute", overflows)
        assertThat(typeService.getByIdOrNull(IdInWs.create("t-sys"))).isNull()
    }

    @Test
    fun artifactDeployIsChecked() {
        val ex = assertThrows<Exception> {
            artifactHandler.deployArtifact(
                TypeDef.create {
                    withId("t-deploy")
                    withModel(model(overflows))
                },
                ""
            )
        }
        assertThat(i18nCause(ex).messageKey).isEqualTo(MSG_TYPE_ATT_TOO_LONG)
        assertThat(typeService.getByIdOrNull(IdInWs.create("t-deploy"))).isNull()
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
