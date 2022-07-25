package ru.citeck.ecos.model.domain.authorities

import org.junit.jupiter.api.Test
import ru.citeck.ecos.context.lib.auth.AuthGroup
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityConstants

class AuthoritiesMixinAttsTest : AuthoritiesTestBase() {

    @Test
    fun test() {

        val group0Ref = createGroup("group-0")
        val group1Ref = createGroup("group-1", AuthorityConstants.ATT_AUTHORITY_GROUPS to group0Ref)

        val user0Ref = createPerson("user-0", AuthorityConstants.ATT_AUTHORITY_GROUPS to listOf(group1Ref))

        assertStrListAtt(
            user0Ref, "authorities.list[]",
            listOf(
                user0Ref.id,
                *listOf(group0Ref, group1Ref).map { AuthGroup.PREFIX + it.id }.toTypedArray(),
                AuthRole.USER,
                AuthGroup.EVERYONE
            )
        )

        assertStrListAtt(
            user0Ref, "${AuthorityConstants.ATT_AUTHORITY_GROUPS}[]?id",
            listOf(
                group1Ref.toString()
            )
        )

        assertStrListAtt(
            user0Ref, "${AuthorityConstants.ATT_AUTHORITY_GROUPS_FULL}[]?id",
            listOf(
                group0Ref.toString(),
                group1Ref.toString()
            )
        )

        assertStrListAtt(
            user0Ref, "${AuthorityConstants.ATT_AUTHORITY_GROUPS_FULL}[]?localId|join()",
            listOf(
                group1Ref.id + "," + group0Ref.id
            )
        )

        assertStrListAtt(
            group1Ref, "${AuthorityConstants.ATT_AUTHORITY_GROUPS_FULL}[]?id",
            listOf(
                group0Ref.toString()
            )
        )
        assertStrListAtt(group0Ref, "${AuthorityConstants.ATT_AUTHORITY_GROUPS_FULL}[]?id", emptyList())
    }
}
