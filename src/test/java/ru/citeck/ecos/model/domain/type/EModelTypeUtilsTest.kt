package ru.citeck.ecos.model.domain.type

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.model.type.service.utils.EModelTypeUtils
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef

class EModelTypeUtilsTest {

    @ParameterizedTest
    @CsvSource(
        "1, 1, t_1",
        "abc__def, abc-def, t_abc_def",
        "12-12abc__def, 12-12abc-def, t_12_12abc_def",
        "a%%%%%b, a-b, t_a_b",
        "ABC, abc, t_abc",
        "a c, a-c, t_a_c",
        "camelCaseTest, camel-case-test, t_camel_case_test",
        "PascalCaseTest, pascal-case-test, t_pascal_case_test",
        "very long type very long type very long type very long type, " +
            "very-long-type-very-long-type-very-gpw5krc, " +
            "t_very_long_type_very_long_type_very_gpw5krc",
        "%aa---bb##$, aa-bb, t_aa_bb",
        "aa:bb--_-=1, aa:bb-1, t_aa__bb_1"
    )
    fun generatedSrcIdAndTableNameTest(typeId: String, expectedSourceId: String, expectedTableId: String) {

        val srcId = EModelTypeUtils.getEmodelSourceId(typeId)
        val tableId = EModelTypeUtils.getEmodelSourceTableId(typeId)

        assertThat(srcId).isEqualTo(expectedSourceId)
        assertThat(tableId).isEqualTo(expectedTableId)
    }

    @Test
    fun sourceIdFromTypeDefTest() {

        assertThat(EModelTypeUtils.getEmodelSourceId(null)).isEmpty()

        val type01 = getTypeDef("""{"id":"test"}""")
        assertThat(EModelTypeUtils.getEmodelSourceId(type01)).isEqualTo("")

        val type02 = getTypeDef("""{"id":"test","sourceId":"abc"}""")
        assertThat(EModelTypeUtils.getEmodelSourceId(type02)).isEqualTo("")

        val type1 = getTypeDef(
            """
            {
                "id":"test",
                "storageType":"ECOS_MODEL"
            }"""
        )
        assertThat(EModelTypeUtils.getEmodelSourceId(type1)).isEqualTo("test")

        val type2 = getTypeDef(
            """
            {
                "id":"test",
                "storageType":"ECOS_MODEL",
                "sourceId":"abc"
            }"""
        )
        assertThat(EModelTypeUtils.getEmodelSourceId(type2)).isEqualTo("abc")

        val type3 = getTypeDef(
            """
            {
                "id":"test",
                "storageType":"ECOS_MODEL",
                "sourceId":"emodel/abc"
            }"""
        )
        assertThat(EModelTypeUtils.getEmodelSourceId(type3)).isEqualTo("abc")

        val type4 = getTypeDef(
            """
            {
                "id":"test",
                "storageType":"ECOS_MODEL",
                "sourceId":"transformations/abc"
            }"""
        )
        assertThat(EModelTypeUtils.getEmodelSourceId(type4)).isEqualTo("test")
    }

    private fun getTypeDef(content: String): TypeDef {
        return Json.mapper.readNotNull(content, TypeDef::class.java)
    }
}
