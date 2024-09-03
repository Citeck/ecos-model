package ru.citeck.ecos.model.domain.authorities

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.data.SimpleAuthData
import ru.citeck.ecos.webapp.api.entity.EntityRef

class AuthoritiesListTest : AuthoritiesTestBase() {

    companion object {
        val DEFAULT_USER_AUTHORITIES = setOf("ROLE_USER", "GROUP_EVERYONE")
    }

    @ParameterizedTest
    @ValueSource(strings = [ "system", "user0", "user1" ])
    fun test(runAsUser: String) {

        val groupsAuthName = ArrayList<String>()
        val groupsRefs = ArrayList<EntityRef>()
        repeat(30) {
            val groupId = "group-$it"
            groupsRefs.add(createGroup(groupId))
            groupsAuthName.add("GROUP_$groupId")
        }

        createPerson("user0", "authorityGroups" to groupsRefs)

        val runAsAuth = when (runAsUser) {
            "system" -> SimpleAuthData("system", AuthContext.SYSTEM_AUTH.getAuthorities())
            "user0" -> SimpleAuthData("user0", emptyList())
            "user1" -> SimpleAuthData("user1", emptyList())
            else -> error("Unknown user: $runAsUser")
        }

        fun getAuthorities(): List<String> {
            return AuthContext.runAs(runAsAuth) {
                recordsService.getAtt(
                    "emodel/person@user0",
                    "authorities.list[]"
                ).asStrList()
            }
        }

        var authorities = getAuthorities()

        val fullAuth = ArrayList(groupsAuthName)
        fullAuth.add("user0")
        fullAuth.addAll(DEFAULT_USER_AUTHORITIES)

        assertThat(authorities).containsExactlyInAnyOrderElementsOf(fullAuth)

        val groupToRemove = "group-15"

        removeUserFromGroup("user0", groupToRemove)
        assertThat(fullAuth.remove("GROUP_$groupToRemove")).isTrue()

        authorities = getAuthorities()
        assertThat(authorities).containsExactlyInAnyOrderElementsOf(fullAuth)

        addUserToGroup("user0", groupToRemove)
        fullAuth.add("GROUP_$groupToRemove")
        authorities = getAuthorities()
        assertThat(authorities).containsExactlyInAnyOrderElementsOf(fullAuth)

        setUserGroups("user0", listOf("group-10", "group-11"))
        authorities = getAuthorities()
        assertThat(authorities).containsExactlyInAnyOrder(
            "user0",
            "GROUP_group-10",
            "GROUP_group-11",
            *DEFAULT_USER_AUTHORITIES.toTypedArray()
        )
    }
}
