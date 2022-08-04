package ru.citeck.ecos.model.domain.type

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import ru.citeck.ecos.model.type.service.utils.EcosModelTypeUtils

class EcosModelTypeUtilsTest {

    @ParameterizedTest
    @CsvSource(
        "1, t-1, t_1",
        "abc__def, t-abc-def, t_abc_def",
        "12-12abc__def, t-12-12abc-def, t_12_12abc_def",
        "a%%%%%b, t-a-b, t_a_b",
        "ABC, t-abc, t_abc",
        "a c, t-a-c, t_a_c",
        "camelCaseTest, t-camel-case-test, t_camel_case_test",
        "PascalCaseTest, t-pascal-case-test, t_pascal_case_test",
        "very long type very long type very long type very long type, " +
            "t-very-long-type-very-long-type-very-gpw5krc, " +
            "t_very_long_type_very_long_type_very_gpw5krc"
    )
    fun test(typeId: String, expectedSourceId: String, expectedTableId: String) {

        val srcId = EcosModelTypeUtils.getEmodelSourceId(typeId)
        val tableId = EcosModelTypeUtils.getEmodelSourceTableId(typeId)

        assertThat(srcId).isEqualTo(expectedSourceId)
        assertThat(tableId).isEqualTo(expectedTableId)
    }
}
