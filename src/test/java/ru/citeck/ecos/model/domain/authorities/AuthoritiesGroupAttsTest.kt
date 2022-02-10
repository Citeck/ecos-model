package ru.citeck.ecos.model.domain.authorities

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AuthoritiesGroupAttsTest : AuthoritiesTestBase() {

    @Test
    fun testName() {

        val name = "SomeName"
        val groupRef = createGroup("group-0", "name" to name)

        listOf("?disp", "name").forEach {
            assertThat(recordsService.getAtt(groupRef, it).asText()).describedAs(it).isEqualTo(name)
        }
    }
}
