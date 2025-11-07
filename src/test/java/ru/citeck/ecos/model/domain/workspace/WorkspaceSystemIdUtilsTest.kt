package ru.citeck.ecos.model.domain.workspace

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import ru.citeck.ecos.model.domain.workspace.utils.WorkspaceSystemIdUtils

class WorkspaceSystemIdUtilsTest {

    @ParameterizedTest
    @CsvSource(
        "1, 1",
        "Q..........................q_, Q_q",
        "abc_____def, abc_def",
        "$$$, RND",
        "----q__q-q-, q_q-q",
        "-q__q-q-, q_q-q",
        "____q__q-q_, q_q-q",
        "____----, RND",
    )
    fun test(input: String, output: String) {
        if (output == "RND") {
            assertThat(WorkspaceSystemIdUtils.createId(input) { false }.matches("[a-z234567]{4,}".toRegex())).isTrue()
        } else {
            assertThat(WorkspaceSystemIdUtils.createId(input) { false }).isEqualTo(output)
        }
    }
}
