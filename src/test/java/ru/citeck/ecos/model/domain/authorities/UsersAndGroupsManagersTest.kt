package ru.citeck.ecos.model.domain.authorities

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.model.domain.authorities.config.GroupDbPermsComponent
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityConstants
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityGroupConstants
import ru.citeck.ecos.model.domain.authorities.patch.CreateDefaultGroupsAndPersonsPatch
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.webapp.api.entity.EntityRef

class UsersAndGroupsManagersTest : AuthoritiesTestBase() {

    companion object {

        val checkPermissionDenied: (Boolean, () -> Unit) -> Unit = { errorExpected, action ->
            if (errorExpected) {
                val ex = assertThrows<Exception> {
                    action.invoke()
                }
                Assertions.assertThat(ex.message?.lowercase())
                    .containsAnyOf("permission denied", "permissions denied")
            } else {
                action.invoke()
            }
        }
    }

    @Test
    fun usersProfileAdminsTest() {

        initTest(authAware = true)
        CreateDefaultGroupsAndPersonsPatch(recordsService).call()

        val profileAdminGroup = AuthorityType.GROUP.getRef(AuthorityGroupConstants.USERS_PROFILE_ADMIN_GROUP)

        val testGroup = AuthContext.runAsSystem {
            createGroup("TEST_GROUP")
        }

        val managerRef: EntityRef = AuthContext.runAsSystem {
            val managerPersonRef = createPerson("manager")
            addAuthorityToGroup(managerPersonRef, profileAdminGroup)
            managerPersonRef
        }

        val regularUser: EntityRef = AuthContext.runAsSystem {
            createPerson("regular-user")
        }

        val testUser: EntityRef = AuthContext.runAsSystem {
            createPerson("test-user")
        }

        AuthContext.runAsFull(managerRef.getLocalId(), getPersonAuthorities(managerRef.getLocalId())) {
            recordsService.mutateAtt(testUser, "firstName", "Tester")
            recordsService.mutateAtt(testUser, "email", "test@example.com")
        }

        Assertions.assertThat(recordsService.getAtt(testUser, "firstName").asText())
            .isEqualTo("Tester")
        Assertions.assertThat(recordsService.getAtt(testUser, "email").asText())
            .isEqualTo("test@example.com")

        /* Mutate by regular user */

        checkPermissionDenied(true) {
            AuthContext.runAsFull(regularUser.getLocalId(), getPersonAuthorities(regularUser.getLocalId())) {
                recordsService.mutateAtt(testUser, "firstName", "TesterNewName")
            }
        }

        /* Creating new user by manager */

        checkPermissionDenied(true) {
            AuthContext.runAsFull(managerRef.getLocalId(), getPersonAuthorities(managerRef.getLocalId())) {
                createPerson("new-user")
            }
        }

        /* authorityGroups attribute mutation by manager */

        checkPermissionDenied(true) {
            AuthContext.runAsFull(managerRef.getLocalId(), getPersonAuthorities(managerRef.getLocalId())) {
                recordsService.mutate(regularUser, mapOf(AuthorityConstants.ATT_AUTHORITY_GROUPS to listOf(testGroup)))
            }
        }

        /* Edit admin users by manager is not allowed */

        val admin = AuthorityType.PERSON.getRef("admin")
        checkPermissionDenied(true) {
            AuthContext.runAsFull(managerRef.getLocalId(), getPersonAuthorities(managerRef.getLocalId())) {
                recordsService.mutateAtt(admin, "firstName", "NewAdminName")
            }
        }
    }

    @Test
    fun groupManagersTest() {

        initTest(authAware = true)

        CreateDefaultGroupsAndPersonsPatch(recordsService).call()

        val managersGroup: EntityRef = AuthorityType.GROUP.getRef(
            AuthorityGroupConstants.GROUPS_MANAGERS_GROUP
        )

        val managedGroupsRoot: EntityRef = AuthorityType.GROUP.getRef(
            AuthorityGroupConstants.MANAGED_GROUPS_GROUP
        )

        val manager: EntityRef = AuthContext.runAsSystem {
            val createdManager = createPerson("manager")
            addAuthorityToGroup(createdManager, managersGroup)
            createdManager
        }

        val testGroup: EntityRef = AuthContext.runAsSystem {
            val testGroup = createGroup("SOME_TEST_GROUP")
            addAuthorityToGroup(testGroup, managedGroupsRoot)
            testGroup
        }

        /* Mutate managed group by manager */

        val newName = MLText(
            I18nContext.ENGLISH to "New name for test group",
            I18nContext.RUSSIAN to "New name for test group"
        )
        AuthContext.runAsFull(manager.getLocalId(), getPersonAuthorities(manager.getLocalId())) {
            recordsService.mutateAtt(testGroup, AuthorityGroupConstants.ATT_NAME, newName)
        }
        Assertions.assertThat(recordsService.getAtt(testGroup, AuthorityGroupConstants.ATT_NAME))
            .isEqualTo(DataValue.create("New name for test group"))

        /* Mutate managed group by regular user */

        val regularUser: EntityRef = AuthContext.runAsSystem {
            createPerson("regular-user")
        }

        checkPermissionDenied(true) {
            AuthContext.runAsFull(regularUser.getLocalId(), getPersonAuthorities(regularUser.getLocalId())) {
                recordsService.mutateAtt(testGroup, AuthorityGroupConstants.ATT_NAME, newName)
            }
        }

        /* Mutate unmanaged group by manager */

        val testUnmanagedGroup: EntityRef = AuthContext.runAsSystem {
            createGroup("SOME_UNMANAGED_TEST_GROUP")
        }

        checkPermissionDenied(true) {
            AuthContext.runAsFull(manager.getLocalId(), getPersonAuthorities(manager.getLocalId())) {
                recordsService.mutateAtt(testUnmanagedGroup, AuthorityGroupConstants.ATT_NAME, newName)
            }
        }

        /* Mutate unmanaged group by regular user */

        checkPermissionDenied(true) {
            AuthContext.runAsFull(regularUser.getLocalId(), getPersonAuthorities(regularUser.getLocalId())) {
                recordsService.mutateAtt(testUnmanagedGroup, AuthorityGroupConstants.ATT_NAME, newName)
            }
        }

        /* Protected groups mutation */

        GroupDbPermsComponent.PROTECTED_GROUPS.forEach {
            checkPermissionDenied(true) {
                AuthContext.runAsFull(manager.getLocalId(), getPersonAuthorities(manager.getLocalId())) {
                    val groupRef = AuthorityType.GROUP.getRef(it)
                    addAuthorityToGroup(regularUser, groupRef)
                }
            }
        }
    }

    private fun getPersonAuthorities(personId: String): List<String> {
        return AuthContext.runAsSystem {
            recordsService.getAtt(AuthorityType.PERSON.getRef(personId), "authorities.list[]").asStrList()
        }
    }
}
