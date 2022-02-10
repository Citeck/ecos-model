package ru.citeck.ecos.model.domain.authorities

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AuthoritiesGroupAttsTest : AuthoritiesTestBase() {

    @Test
    fun testName() {

        val name = "SomeName"
        val groupRef = createGroup("group-0", "name" to name)

        listOf("?disp", "name").forEach {
            assertThat(recordsService.getAtt(groupRef, it).asText()).describedAs(it).isEqualTo(name)
        }
    }

    @Test
    fun cyclicDepsTest() {

        val child = createGroup("child-group")
        val parent0 = createGroup("parent-0")
        val parent1 = createGroup("parent-1")
        val parent2 = createGroup("parent-2")

        addAuthorityToGroup(child, parent0)
        addAuthorityToGroup(parent0, parent1)
        addAuthorityToGroup(parent1, parent2)

        val ex = assertThrows<Exception> {
            addAuthorityToGroup(parent2, parent0)
        }
        assertThat(ex.message).contains("Cyclic dependency")
    }
}
