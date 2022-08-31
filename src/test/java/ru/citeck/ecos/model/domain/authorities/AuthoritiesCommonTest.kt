package ru.citeck.ecos.model.domain.authorities

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.auth.*
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityConstants
import ru.citeck.ecos.model.domain.authsync.service.AuthorityType
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery

class AuthoritiesCommonTest : AuthoritiesTestBase() {

    @Test
    fun authTest() {

        initTest(authAware = true)

        val checkPermissionDenied: (Boolean, () -> Unit) -> Unit = { errorExpected, action ->
            if (errorExpected) {
                val ex = assertThrows<Exception> {
                    action.invoke()
                }
                assertThat(ex.message).contains("Permission denied")
            } else {
                action.invoke()
            }
        }
        checkPermissionDenied(true) {
            createPerson("test-user")
        }
        checkPermissionDenied(false) {
            AuthContext.runAsSystem {
                createPerson("test-user")
            }
        }
        checkPermissionDenied(true) {
            createGroup("test-group")
        }
        checkPermissionDenied(false) {
            AuthContext.runAsSystem {
                createGroup("test-group")
            }
        }
    }

    @Test
    fun test() {

        val testPersonRef = AuthorityType.PERSON.getRef("test-person")
        val testPersonFirstName = "Test First Name"

        val personAtts = ObjectData.create()
        personAtts["id"] = testPersonRef.id
        personAtts["firstName"] = testPersonFirstName

        val personCreateResult = AuthContext.runAsSystem {
            recordsService.create(testPersonRef.sourceId, personAtts)
        }

        assertThat(personCreateResult.id).isEqualTo(testPersonRef.id)

        val testGroupRef = AuthorityType.GROUP.getRef("test-group")
        val groupAtts = ObjectData.create()
        groupAtts["id"] = testGroupRef.id
        groupAtts["name"] = MLText("Test Group Name")

        val groupCreateResult = AuthContext.runAsSystem {
            recordsService.create(testGroupRef.sourceId, groupAtts)
        }
        assertThat(groupCreateResult.id).isEqualTo(testGroupRef.id)

        val checkAuthorities = { expected: List<String> ->
            val currentAuthorities = recordsService.getAtt(testPersonRef, "authorities.list[]").asStrList()
            assertThat(currentAuthorities).containsExactlyElementsOf(expected)
        }

        val addGroup = { groupId: String, runAs: String ->
            val groupRef = AuthorityType.GROUP.getRef(groupId)
            val notExists = recordsService.getAtt(groupRef, RecordConstants.ATT_NOT_EXISTS).asBoolean()
            if (notExists) {
                val newGroupAtts = ObjectData.create()
                newGroupAtts.set("id", groupRef.id)
                AuthContext.runAsSystem {
                    recordsService.create(AuthorityType.GROUP.sourceId, newGroupAtts)
                }
            }
            val mutateAction = {
                recordsService.mutateAtt(
                    testPersonRef,
                    "att_add_${AuthorityConstants.ATT_AUTHORITY_GROUPS}",
                    groupRef
                )
            }
            if (runAs.isNotEmpty()) {
                AuthContext.runAs(runAs) { mutateAction() }
            } else {
                mutateAction()
            }
        }

        checkAuthorities(
            listOf(
                testPersonRef.id,
                AuthGroup.EVERYONE,
                AuthRole.USER
            )
        )

        AuthContext.runAsSystem {
            recordsService.mutate(testPersonRef, mapOf(AuthorityConstants.ATT_AUTHORITY_GROUPS to listOf(testGroupRef)))
        }

        checkAuthorities(
            listOf(
                testPersonRef.id,
                "GROUP_${testGroupRef.id}",
                AuthGroup.EVERYONE,
                AuthRole.USER
            )
        )

        val otherGroupId = "other-group"
        val otherGroupRef = AuthorityType.GROUP.getRef(otherGroupId)
        addGroup(otherGroupId, "system")

        checkAuthorities(
            listOf(
                testPersonRef.id,
                "GROUP_${testGroupRef.id}",
                "GROUP_$otherGroupId",
                AuthGroup.EVERYONE,
                AuthRole.USER
            )
        )

        listOf(
            "" to true,
            testPersonRef.id to true,
            "admin" to false,
            AuthUser.SYSTEM to false
        ).forEach {
            println("Check user '${it.first}' exception expected: ${it.second}")
            val runAs = if (it.first.isEmpty()) {
                { action: () -> Unit -> action() }
            } else {
                { action: () -> Unit ->
                    if (it.first == "admin") {
                        AuthContext.runAs(it.first, listOf(AuthRole.ADMIN), action)
                    } else {
                        AuthContext.runAs(it.first, action)
                    }
                }
            }
            runAs {
                if (it.second) {
                    val ex = assertThrows<Exception> {
                        addGroup("other-auth-group-${it.first}", it.first)
                    }
                    assertThat(ex.message).contains("Permission denied")
                } else {
                    addGroup("other-auth-group-${it.first}", it.first)
                }
            }
        }

        checkAuthorities(
            listOf(
                testPersonRef.id,
                "GROUP_${testGroupRef.id}",
                "GROUP_$otherGroupId",
                "GROUP_other-auth-group-admin",
                "GROUP_other-auth-group-${AuthUser.SYSTEM}",
                AuthGroup.EVERYONE,
                AuthRole.USER
            )
        )

        assertThrows<Exception> {
            recordsService.mutateAtt(testPersonRef, "email", "test@test.ru")
        }
        AuthContext.runAs(testPersonRef.id) {
            recordsService.mutateAtt(testPersonRef, "email", "test@test.ru")
        }
        assertThat(recordsService.getAtt(testPersonRef, "email").asText()).isEqualTo("test@test.ru")

        val newParentGroupRef = RecordRef.create(AuthorityType.GROUP.sourceId, "new-parent-group")
        AuthContext.runAsSystem {
            val groupRef = RecordRef.create(AuthorityType.GROUP.sourceId, otherGroupId)
            recordsService.mutateAtt(groupRef, "att_add_authorityGroups", newParentGroupRef)
        }

        checkAuthorities(
            listOf(
                testPersonRef.id,
                "GROUP_${testGroupRef.id}",
                "GROUP_$otherGroupId",
                "GROUP_${newParentGroupRef.id}",
                "GROUP_other-auth-group-admin",
                "GROUP_other-auth-group-${AuthUser.SYSTEM}",
                AuthGroup.EVERYONE,
                AuthRole.USER
            )
        )

        AuthContext.runAsSystem {
            recordsService.mutateAtt(otherGroupRef, "authorityGroups", emptyList<Any>())
        }
        checkAuthorities(
            listOf(
                testPersonRef.id,
                "GROUP_${testGroupRef.id}",
                "GROUP_$otherGroupId",
                "GROUP_other-auth-group-admin",
                "GROUP_other-auth-group-${AuthUser.SYSTEM}",
                AuthGroup.EVERYONE,
                AuthRole.USER
            )
        )

        AuthContext.runAsSystem {
            recordsService.mutateAtt(
                testPersonRef,
                "att_rem_authorityGroups",
                listOf(AuthorityType.GROUP.getRef(otherGroupId))
            )
        }
        checkAuthorities(
            listOf(
                testPersonRef.id,
                "GROUP_${testGroupRef.id}",
                "GROUP_other-auth-group-admin",
                "GROUP_other-auth-group-${AuthConstants.SYSTEM_USER}",
                AuthGroup.EVERYONE,
                AuthRole.USER
            )
        )

        addGroup(otherGroupId, "system")

        val groupsChain = Array(3) {
            "group-$it"
        }
        AuthContext.runAsSystem {
            groupsChain.forEach {
                val atts = ObjectData.create()
                atts["id"] = it
                recordsService.create(AuthorityType.GROUP.sourceId, atts)
            }
        }
        AuthContext.runAsSystem {
            for (idx in groupsChain.indices) {
                val sourceGroupId = if (idx == 0) {
                    otherGroupId
                } else {
                    groupsChain[idx - 1]
                }
                recordsService.mutateAtt(
                    AuthorityType.GROUP.getRef(sourceGroupId),
                    "authorityGroups",
                    AuthorityType.GROUP.getRef(groupsChain[idx])
                )
            }
        }
        checkAuthorities(
            listOf(
                testPersonRef.id,
                "GROUP_${testGroupRef.id}",
                "GROUP_other-auth-group-admin",
                "GROUP_other-auth-group-${AuthUser.SYSTEM}",
                "GROUP_$otherGroupId",
                "GROUP_group-0",
                "GROUP_group-1",
                "GROUP_group-2",
                AuthGroup.EVERYONE,
                AuthRole.USER
            )
        )

        listOf(
            *groupsChain,
            otherGroupId,
            testGroupRef.id
        ).forEach { groupId ->

            val query = RecordsQuery.create {
                withSourceId(AuthorityType.PERSON.sourceId)
                withQuery(
                    Predicates.contains(
                        AuthorityConstants.ATT_AUTHORITY_GROUPS_FULL,
                        AuthorityType.GROUP.getRef(groupId).toString()
                    )
                )
            }

            val queryRes = recordsService.query(query)
            assertThat(queryRes.getRecords()).describedAs("query: %s", query.query).hasSize(1)
            assertThat(queryRes.getRecords()[0]).isEqualTo(testPersonRef)
        }

        AuthContext.runAsSystem {
            val ref = AuthorityType.GROUP.getRef("group-1")
            recordsService.mutateAtt(ref, "authorityGroups", emptyList<Any>())
        }

        checkAuthorities(
            listOf(
                testPersonRef.id,
                "GROUP_${testGroupRef.id}",
                "GROUP_other-auth-group-admin",
                "GROUP_other-auth-group-${AuthUser.SYSTEM}",
                "GROUP_$otherGroupId",
                "GROUP_group-0",
                "GROUP_group-1",
                AuthGroup.EVERYONE,
                AuthRole.USER
            )
        )
    }
}
