package ru.citeck.ecos.model.domain.workspace

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.apps.app.service.LocalAppService
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.utils.resource.ResourceUtils
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthGroup
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.context.lib.auth.data.AuthData
import ru.citeck.ecos.context.lib.auth.data.SimpleAuthData
import ru.citeck.ecos.model.AuthoritiesHelper
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.model.domain.workspace.api.records.WorkspaceServiceRecordsDao
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceMemberDesc
import ru.citeck.ecos.model.domain.workspace.dto.Workspace
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMemberRole
import ru.citeck.ecos.model.domain.workspace.service.EmodelWorkspaceService
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosModelApp::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkspaceServiceTest {

    companion object {
        private const val USER_0_ID = "user0"
        private const val GROUP_0_ID = "group0"

        private val USER_0_AUTH = SimpleAuthData(
            USER_0_ID,
            listOf(AuthRole.USER, AuthGroup.PREFIX + GROUP_0_ID)
        )
    }

    @Autowired
    private lateinit var recordsService: RecordsService

    @Autowired
    private lateinit var localAppService: LocalAppService

    @Autowired
    private lateinit var workspaceService: EmodelWorkspaceService

    @Autowired
    private lateinit var authoritiesHelper: AuthoritiesHelper

    private val refsToDelete = mutableListOf<EntityRef>()

    @BeforeAll
    fun setUp() {
        localAppService.deployLocalArtifacts(ResourceUtils.getFile("classpath:eapps/artifacts"))

        refsToDelete.add(authoritiesHelper.createPerson(USER_0_ID))
        refsToDelete.add(authoritiesHelper.createGroup(GROUP_0_ID))
        authoritiesHelper.addUserToGroup(USER_0_ID, GROUP_0_ID)
    }

    @AfterAll
    fun tearDown() {
        AuthContext.runAsSystem {
            recordsService.delete(refsToDelete)
        }
    }

    @Test
    fun isDirectMemberTest() {

        val workspaceId = workspaceService.deployWorkspace(
            Workspace.create().withId("test-ws")
                .withName(MLText("test-ws"))
                .build()
        )
        val workspaceRef = WorkspaceDesc.getRef(workspaceId)

        fun assertMember(user: AuthData?, direct: Boolean, expected: Boolean) {
            val attToCheck = if (direct) {
                WorkspaceDesc.ATT_IS_CURRENT_USER_DIRECT_MEMBER + ScalarType.BOOL_SCHEMA
            } else {
                WorkspaceDesc.ATT_IS_CURRENT_USER_MEMBER + ScalarType.BOOL_SCHEMA
            }
            if (user == null) {
                assertThat(recordsService.getAtt(workspaceRef, attToCheck).asBoolean()).isEqualTo(expected)
            } else {
                AuthContext.runAsFull(user) {
                    assertThat(recordsService.getAtt(workspaceRef, attToCheck).asBoolean()).isEqualTo(expected)
                }
            }
        }

        fun assertUserLastManager(expected: Boolean) {
            val isLastManagerAtt = WorkspaceDesc.ATT_IS_CURRENT_USER_LAST_MANAGER + ScalarType.BOOL_SCHEMA
            AuthContext.runAsFull(USER_0_AUTH) {
                assertThat(recordsService.getAtt(workspaceRef, isLastManagerAtt)).isEqualTo(DataValue.of(expected))
            }
        }

        assertMember(null, direct = true, expected = false)
        assertMember(null, direct = false, expected = false)
        assertMember(USER_0_AUTH, direct = true, expected = false)
        assertMember(USER_0_AUTH, direct = false, expected = false)

        assertUserLastManager(false)

        val groupMember = recordsService.create(
            WorkspaceMemberDesc.SOURCE_ID, mapOf(
                RecordConstants.ATT_PARENT to workspaceRef,
                RecordConstants.ATT_PARENT_ATT to WorkspaceDesc.ATT_WORKSPACE_MEMBERS,
                WorkspaceMemberDesc.ATT_AUTHORITIES to AuthorityType.GROUP.getRef(GROUP_0_ID),
                WorkspaceMemberDesc.ATT_MEMBER_ROLE to WorkspaceMemberRole.MANAGER
            ))

        assertMember(USER_0_AUTH, direct = true, expected = false)
        assertMember(USER_0_AUTH, direct = false, expected = true)

        assertUserLastManager(false)

        recordsService.create(
            WorkspaceMemberDesc.SOURCE_ID, mapOf(
                RecordConstants.ATT_PARENT to workspaceRef,
                RecordConstants.ATT_PARENT_ATT to WorkspaceDesc.ATT_WORKSPACE_MEMBERS,
                WorkspaceMemberDesc.ATT_AUTHORITIES to AuthorityType.PERSON.getRef(USER_0_AUTH.getUser()),
                WorkspaceMemberDesc.ATT_MEMBER_ROLE to WorkspaceMemberRole.MANAGER
            )
        )

        assertMember(USER_0_AUTH, direct = true, expected = false)
        assertMember(USER_0_AUTH, direct = false, expected = true)

        assertUserLastManager(false)

        recordsService.delete(groupMember)

        assertMember(USER_0_AUTH, direct = true, expected = true)
        assertMember(USER_0_AUTH, direct = false, expected = true)

        assertUserLastManager(true)

        recordsService.delete(workspaceRef)
    }

    @Test
    fun leaveJoinTest() {

        val testUser1 = "test-user-1"

        val workspaceId = runAsUser(testUser1) {
            workspaceService.deployWorkspace(
                Workspace.create().withId("test-ws")
                    .withName(MLText("test-ws"))
                    .build()
            )
        }

        val managers = workspaceService.getWorkspaceManagersRefs(workspaceId)
        assertThat(managers).containsExactly(AuthorityType.PERSON.getRef(testUser1))

        val leaveAction = RecordAtts(EntityRef.create(WorkspaceServiceRecordsDao.ID, ""))
        leaveAction["workspace"] = workspaceId
        leaveAction["type"] = "LEAVE"

        val exception = runAsUser(testUser1) {
            assertThrows<RuntimeException> {
                recordsService.mutate(leaveAction)
            }
        }
        assertThat(exception.message).contains("You can't leave workspace when you are last manager")

        val joinAction = RecordAtts(EntityRef.create(WorkspaceServiceRecordsDao.ID, ""))
        joinAction["workspace"] = workspaceId
        joinAction["type"] = "JOIN"

        val testUser2 = "test-user-2"
        runAsUser(testUser2) {
            recordsService.mutate(joinAction)
        }

        val newMember = workspaceService.getWorkspace(workspaceId)
            .workspaceMembers
            .find { member -> member.authorities.any { it.getLocalId() == testUser2 } }
        assertThat(newMember).isNotNull
        assertThat(newMember!!.memberRole).isEqualTo(WorkspaceMemberRole.USER)

        val managers2 = workspaceService.getWorkspaceManagersRefs(workspaceId)
        assertThat(managers2).containsExactly(AuthorityType.PERSON.getRef(testUser1))

        val exception2 = runAsUser(testUser1) {
            assertThrows<RuntimeException> {
                recordsService.mutate(leaveAction)
            }
        }
        assertThat(exception2.message).contains("You can't leave workspace when you are last manager")

        recordsService.create(
            WorkspaceMemberDesc.SOURCE_ID, mapOf(
            RecordConstants.ATT_PARENT to WorkspaceDesc.getRef(workspaceId),
            RecordConstants.ATT_PARENT_ATT to WorkspaceDesc.ATT_WORKSPACE_MEMBERS,
            WorkspaceMemberDesc.ATT_AUTHORITIES to AuthorityType.PERSON.getRef(testUser2),
            WorkspaceMemberDesc.ATT_MEMBER_ROLE to WorkspaceMemberRole.MANAGER
        ))

        val managers3 = workspaceService.getWorkspaceManagersRefs(workspaceId)
        assertThat(managers3).containsExactlyInAnyOrder(
            AuthorityType.PERSON.getRef(testUser1),
            AuthorityType.PERSON.getRef(testUser2)
        )

        runAsUser(testUser1) {
            recordsService.mutate(leaveAction)
        }

        val managers4 = workspaceService.getWorkspaceManagersRefs(workspaceId)
        assertThat(managers4).containsExactlyInAnyOrder(
            AuthorityType.PERSON.getRef(testUser2)
        )

        recordsService.delete(WorkspaceDesc.getRef(workspaceId))
    }

    private inline fun <T> runAsUser(user: String, crossinline action: () -> T): T {
        return AuthContext.runAsFull(user, listOf(AuthRole.USER)) {
            action.invoke()
        }
    }
}
