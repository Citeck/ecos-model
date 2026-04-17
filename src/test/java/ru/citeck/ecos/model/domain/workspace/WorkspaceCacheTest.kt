package ru.citeck.ecos.model.domain.workspace

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.apps.app.service.LocalAppService
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.context.lib.auth.data.SimpleAuthData
import ru.citeck.ecos.model.AuthoritiesHelper
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceMemberDesc
import ru.citeck.ecos.model.domain.workspace.dto.Workspace
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMember
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMemberRole
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceVisibility
import ru.citeck.ecos.model.domain.workspace.service.CustomWorkspaceApi
import ru.citeck.ecos.model.domain.workspace.service.EmodelWorkspaceService
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.model.lib.workspace.api.WsMembershipType
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosModelApp::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkspaceCacheTest {

    companion object {
        private const val USER_A = "cache-user-a"
        private const val USER_B = "cache-user-b"
        private const val GROUP_A = "cache-group-a"
    }

    @Autowired
    private lateinit var recordsService: RecordsService

    @Autowired
    private lateinit var localAppService: LocalAppService

    @Autowired
    private lateinit var workspaceService: EmodelWorkspaceService

    @Autowired
    private lateinit var customWorkspaceApi: CustomWorkspaceApi

    @Autowired
    private lateinit var authoritiesHelper: AuthoritiesHelper

    private val workspaceRefsToDelete = mutableListOf<EntityRef>()
    private val authorityRefsToDelete = mutableListOf<EntityRef>()

    @BeforeAll
    fun setUp() {
        AuthContext.runAsSystem {
            localAppService.deployLocalArtifacts("model/workspace")
        }
        authorityRefsToDelete.add(authoritiesHelper.createPerson(USER_A))
        authorityRefsToDelete.add(authoritiesHelper.createPerson(USER_B))
        authorityRefsToDelete.add(authoritiesHelper.createGroup(GROUP_A))
    }

    @AfterAll
    fun tearDown() {
        AuthContext.runAsSystem {
            // Workspaces must be deleted before their member authorities to avoid
            // "last manager" validation errors triggered by authority deletion.
            recordsService.delete(workspaceRefsToDelete)
            recordsService.delete(authorityRefsToDelete)
        }
    }

    private fun createWorkspace(id: String, members: List<WorkspaceMember>): String {
        val wsId = workspaceService.deployWorkspace(
            Workspace.create()
                .withId(id)
                .withName(MLText(id))
                .withWorkspaceMembers(members)
                .build()
        )
        workspaceRefsToDelete.add(WorkspaceDesc.getRef(wsId))
        return wsId
    }

    private fun managerMember(memberId: String, authority: EntityRef) = WorkspaceMember.create()
        .withMemberId(memberId)
        .withAuthorities(listOf(authority))
        .withMemberRole(WorkspaceMemberRole.MANAGER)
        .build()

    @Test
    fun userWorkspacesCacheInvalidatedOnMemberAdd() {
        val wsId = createWorkspace(
            "cache-test-ws-add",
            listOf(managerMember("m0", AuthorityType.PERSON.getRef(USER_B)))
        )

        val initial = AuthContext.runAsSystem {
            customWorkspaceApi.getUserWorkspaces(USER_A, WsMembershipType.DIRECT)
        }
        assertThat(initial).doesNotContain(wsId)

        AuthContext.runAsSystem {
            recordsService.create(
                WorkspaceMemberDesc.SOURCE_ID,
                mapOf(
                    RecordConstants.ATT_PARENT to WorkspaceDesc.getRef(wsId),
                    RecordConstants.ATT_PARENT_ATT to WorkspaceDesc.ATT_WORKSPACE_MEMBERS,
                    WorkspaceMemberDesc.ATT_AUTHORITIES to AuthorityType.PERSON.getRef(USER_A),
                    WorkspaceMemberDesc.ATT_MEMBER_ROLE to WorkspaceMemberRole.USER
                )
            )
        }

        val after = AuthContext.runAsSystem {
            customWorkspaceApi.getUserWorkspaces(USER_A, WsMembershipType.DIRECT)
        }
        assertThat(after).contains(wsId)
    }

    @Test
    fun isUserManagerOfCacheInvalidatedOnMemberDelete() {
        val memberRef = AuthContext.runAsSystem {
            val wsId = createWorkspace(
                "cache-test-ws-mgr",
                listOf(
                    managerMember("m0", AuthorityType.PERSON.getRef(USER_B)),
                    managerMember("m1", AuthorityType.PERSON.getRef(USER_A))
                )
            )
            val members = recordsService.getAtt(WorkspaceDesc.getRef(wsId), "workspaceMembers[]?id")
                .asList(EntityRef::class.java)
            assertThat(customWorkspaceApi.isUserManagerOf(USER_A, wsId)).isTrue()
            members.first { m ->
                recordsService.getAtt(m, WorkspaceMemberDesc.ATT_MEMBER_ID).asText() == "m1"
            } to wsId
        }
        val wsId = memberRef.second

        AuthContext.runAsSystem {
            recordsService.delete(memberRef.first)
        }

        val after = AuthContext.runAsSystem {
            customWorkspaceApi.isUserManagerOf(USER_A, wsId)
        }
        assertThat(after).isFalse()
    }

    @Test
    fun joinEvictsUserWorkspacesCacheSynchronously() {
        val wsId = AuthContext.runAsSystem {
            workspaceService.deployWorkspace(
                Workspace.create()
                    .withId("cache-test-ws-join")
                    .withName(MLText("cache-test-ws-join"))
                    .withVisibility(WorkspaceVisibility.PUBLIC)
                    .withWorkspaceMembers(
                        listOf(managerMember("m0", AuthorityType.PERSON.getRef(USER_B)))
                    )
                    .build()
            ).also { workspaceRefsToDelete.add(WorkspaceDesc.getRef(it)) }
        }

        val before = AuthContext.runAsSystem {
            customWorkspaceApi.getUserWorkspaces(USER_A, WsMembershipType.DIRECT)
        }
        assertThat(before).doesNotContain(wsId)

        AuthContext.runAsFull(SimpleAuthData(USER_A, listOf(AuthRole.USER))) {
            workspaceService.joinCurrentUserToWorkspace(wsId)
        }

        val after = AuthContext.runAsSystem {
            customWorkspaceApi.getUserWorkspaces(USER_A, WsMembershipType.DIRECT)
        }
        assertThat(after).contains(wsId)
    }

    @Test
    fun groupAuthorityGroupsChangeEvictsAllUserWorkspaces() {
        val parentGroupRef = authoritiesHelper.createGroup("cache-parent-group")
        authorityRefsToDelete.add(parentGroupRef)
        val parentGroupId = parentGroupRef.getLocalId()

        // Workspace is managed by parent-group. USER_A is in GROUP_A, which is not yet
        // a child of parent-group, so USER_A does not see the workspace initially.
        val wsId = createWorkspace(
            "cache-test-ws-parent-group",
            listOf(managerMember("m0", AuthorityType.GROUP.getRef(parentGroupId)))
        )
        AuthContext.runAsSystem {
            authoritiesHelper.addUserToGroup(USER_A, GROUP_A)
        }

        // Populate the cache with the stale empty value.
        val before = AuthContext.runAsSystem {
            customWorkspaceApi.getUserWorkspaces(USER_A, WsMembershipType.ALL)
        }
        assertThat(before).doesNotContain(wsId)

        // Make GROUP_A a child of parent-group. Fires GroupGroupsChanged on GROUP_A,
        // which must flush the userWorkspaces cache; otherwise USER_A keeps the stale set.
        AuthContext.runAsSystem {
            recordsService.mutateAtt(
                AuthorityType.GROUP.getRef(GROUP_A),
                "att_add_authorityGroups",
                AuthorityType.GROUP.getRef(parentGroupId)
            )
        }

        val after = AuthContext.runAsSystem {
            customWorkspaceApi.getUserWorkspaces(USER_A, WsMembershipType.ALL)
        }
        assertThat(after).contains(wsId)
    }
}
