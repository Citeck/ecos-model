package ru.citeck.ecos.model.domain.workspace

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.apps.app.service.LocalAppService
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.utils.resource.ResourceUtils
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.context.lib.auth.AuthUser
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.model.domain.workspace.api.records.WorkspaceProxyDao.Companion.WORKSPACE_ACTION_ATT
import ru.citeck.ecos.model.domain.workspace.api.records.WorkspaceProxyDao.Companion.WORKSPACE_SOURCE_ID
import ru.citeck.ecos.model.domain.workspace.dto.Workspace
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceAction
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceVisibility
import ru.citeck.ecos.model.domain.workspace.service.EmodelWorkspaceService
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.records3.record.dao.query.dto.query.QueryPage
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.authority.EcosAuthoritiesApi
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.util.UUID
import java.util.stream.Stream
import kotlin.test.assertTrue

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosModelApp::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkspacePermissionsTest {

    @Autowired
    private lateinit var recordsService: RecordsService

    @Autowired
    private lateinit var localAppService: LocalAppService

    @Autowired
    private lateinit var workspaceService: EmodelWorkspaceService

    @Autowired
    private lateinit var ecosAuthoritiesApi: EcosAuthoritiesApi

    companion object {

        private const val HOGWARTS_WORKSPACE = "hogwarts-workspace"
        private const val GRYFFINDOR_WORKSPACE = "gryffindor-workspace"
        private const val SLYTHERIN_WORKSPACE = "slytherin-workspace"
        private const val DIAGON_ALLEY_WORKSPACE = "diagon-alley-workspace"

        private val allWorkspacesRefs = listOf(
            HOGWARTS_WORKSPACE.toWorkspaceRef(),
            GRYFFINDOR_WORKSPACE.toWorkspaceRef(),
            SLYTHERIN_WORKSPACE.toWorkspaceRef(),
            DIAGON_ALLEY_WORKSPACE.toWorkspaceRef()
        )

        @JvmStatic
        fun workspaceVisibilityProvider(): Stream<WorkspaceVisibility> {
            return Stream.of(*WorkspaceVisibility.entries.toTypedArray())
        }
    }

    @BeforeAll
    fun setUp() {
        AuthContext.runAsSystem {
            localAppService.deployLocalArtifacts(ResourceUtils.getFile("classpath:eapps/artifacts"))
        }
    }

    @Test
    fun `system should see all workspaces`() {
        val workspaces = queryWorkspacesFor(AuthUser.SYSTEM)

        assertThat(workspaces).containsAll(allWorkspacesRefs)
    }

    @Test
    fun `admin role should see all workspaces`() {
        val workspaces = queryWorkspacesFor("someAdmin", listOf(AuthRole.ADMIN))

        assertThat(workspaces).containsAll(allWorkspacesRefs)
    }

    @ParameterizedTest
    @ValueSource(strings = [AuthRole.ANONYMOUS, AuthRole.GUEST])
    fun `not authenticated auth should not see any workspaces`(role: String) {
        val workspaces = queryWorkspacesFor("someNotAuth", listOf(role))

        assertThat(workspaces).isEmpty()
    }

    @Test
    fun `user role without private workspace should see all public workspaces`() {
        val workspaces = queryWorkspacesFor("someUser", listOf(AuthRole.USER))

        assertThat(workspaces).containsAll(
            listOf(
                HOGWARTS_WORKSPACE.toWorkspaceRef(),
                DIAGON_ALLEY_WORKSPACE.toWorkspaceRef()
            )
        )
    }

    @Test
    fun `user with multiple roles should see appropriate workspaces`() {
        val workspaces = queryWorkspacesFor("multiRoleUser", listOf(AuthRole.USER, "GROUP_gryffindor-managers"))

        assertThat(workspaces).containsAll(
            listOf(
                HOGWARTS_WORKSPACE.toWorkspaceRef(),
                DIAGON_ALLEY_WORKSPACE.toWorkspaceRef(),
                GRYFFINDOR_WORKSPACE.toWorkspaceRef()
            )
        )
    }

    @Test
    fun `user with no roles should not see any workspaces`() {
        val workspaces = queryWorkspacesFor("noRoleUser")

        assertThat(workspaces).isEmpty()
    }

    @Test
    fun `user role with private workspace should see own workspace and public workspaces`() {
        val workspaces = queryWorkspacesFor("harry", listOf(AuthRole.USER))

        assertThat(workspaces).containsAll(
            listOf(
                HOGWARTS_WORKSPACE.toWorkspaceRef(),
                DIAGON_ALLEY_WORKSPACE.toWorkspaceRef(),
                GRYFFINDOR_WORKSPACE.toWorkspaceRef()
            )
        )
    }

    @Test
    fun `user in group with private workspace should see own workspace and public workspaces`() {
        val workspaces = queryWorkspacesFor("someSlytherinMember", listOf(AuthRole.USER, "GROUP_slytherin-group"))

        assertThat(workspaces).containsAll(
            listOf(
                HOGWARTS_WORKSPACE.toWorkspaceRef(),
                DIAGON_ALLEY_WORKSPACE.toWorkspaceRef(),
                SLYTHERIN_WORKSPACE.toWorkspaceRef()
            )
        )
    }

    @ParameterizedTest
    @ValueSource(strings = [AuthRole.ADMIN, AuthRole.SYSTEM])
    fun `admin and system should allow edit all workspaces`(role: String) {
        AuthContext.runAs("someUser", listOf(role)) {
            allWorkspacesRefs.forEach { workspaceRef ->
                val allowWrite = recordsService.getAtt(workspaceRef, "permissions._has.Write?bool!").asBoolean()
                assertTrue(allowWrite)
            }
        }
    }

    @Test
    fun `manager should allow edit own private workspace`() {
        val workspaceRef = GRYFFINDOR_WORKSPACE.toWorkspaceRef()
        AuthContext.runAs("harry", listOf(AuthRole.USER)) {
            val allowWrite = recordsService.getAtt(workspaceRef, "permissions._has.Write?bool!").asBoolean()
            assertTrue(allowWrite)
        }
    }

    @Test
    fun `manager should allow edit own public workspace`() {
        val workspaceRef = HOGWARTS_WORKSPACE.toWorkspaceRef()
        AuthContext.runAs("dumbledore", listOf(AuthRole.USER)) {
            val allowWrite = recordsService.getAtt(workspaceRef, "permissions._has.Write?bool!").asBoolean()
            assertTrue(allowWrite)
        }
    }

    @Test
    fun `manager group should allow edit own private workspace`() {
        val workspaceRef = GRYFFINDOR_WORKSPACE.toWorkspaceRef()
        AuthContext.runAs("someManager", listOf("GROUP_gryffindor-managers", AuthRole.USER)) {
            val allowWrite = recordsService.getAtt(workspaceRef, "permissions._has.Write?bool!").asBoolean()
            assertTrue(allowWrite)
        }
    }

    @Test
    fun `manager group should allow edit own public workspace`() {
        val workspaceRef = DIAGON_ALLEY_WORKSPACE.toWorkspaceRef()
        AuthContext.runAs("someManager", listOf("GROUP_diagon-alley-managers", AuthRole.USER)) {
            val allowWrite = recordsService.getAtt(workspaceRef, "permissions._has.Write?bool!").asBoolean()
            assertTrue(allowWrite)
        }
    }

    @Test
    fun `manager should not allow edit other workspace`() {
        val workspaceRef = SLYTHERIN_WORKSPACE.toWorkspaceRef()
        AuthContext.runAs("harry", listOf(AuthRole.USER)) {
            val restrictWrite = recordsService.getAtt(workspaceRef, "permissions._has.Write?bool!").asBoolean().not()
            assertTrue(restrictWrite)
        }
    }

    @Test
    fun `manager group should not allow edit other workspace`() {
        val workspaceRef = GRYFFINDOR_WORKSPACE.toWorkspaceRef()
        AuthContext.runAs("someSlytherinMember", listOf("GROUP_slytherin-group", AuthRole.USER)) {
            val restrictWrite = recordsService.getAtt(workspaceRef, "permissions._has.Write?bool!").asBoolean().not()
            assertTrue(restrictWrite)
        }
    }

    @Test
    fun `user with user role should not allow edit own workspaces`() {
        val workspaceRef = GRYFFINDOR_WORKSPACE.toWorkspaceRef()
        AuthContext.runAs("ron", listOf(AuthRole.USER)) {
            val restrictWrite = recordsService.getAtt(workspaceRef, "permissions._has.Write?bool!").asBoolean().not()
            assertTrue(restrictWrite)
        }
    }

    @Test
    fun `user with user role should not allow edit other workspaces`() {
        val workspaceRef = SLYTHERIN_WORKSPACE.toWorkspaceRef()
        AuthContext.runAs("ron", listOf(AuthRole.USER)) {
            val restrictWrite = recordsService.getAtt(workspaceRef, "permissions._has.Write?bool!").asBoolean().not()
            assertTrue(restrictWrite)
        }
    }

    @Test
    fun `group with user role should not allow edit own workspaces`() {
        val workspaceRef = SLYTHERIN_WORKSPACE.toWorkspaceRef()
        AuthContext.runAs("someSlytherinMember", listOf("GROUP_slytherin-group", AuthRole.USER)) {
            val restrictWrite = recordsService.getAtt(workspaceRef, "permissions._has.Write?bool!").asBoolean().not()
            assertTrue(restrictWrite)
        }
    }

    @Test
    fun `group with user role should not allow edit other workspaces`() {
        val workspaceRef = SLYTHERIN_WORKSPACE.toWorkspaceRef()
        AuthContext.runAs("someSlytherinMember", listOf("GROUP_slytherin-group", AuthRole.USER)) {
            val restrictWrite = recordsService.getAtt(workspaceRef, "permissions._has.Write?bool!").asBoolean().not()
            assertTrue(restrictWrite)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = [AuthRole.USER, AuthRole.ADMIN, AuthRole.SYSTEM])
    fun `all authenticated users can create public workspaces`(role: String) {
        val workspace = Workspace(
            id = UUID.randomUUID().toString(),
            name = MLText("Test workspace"),
            visibility = WorkspaceVisibility.PUBLIC
        )

        val createdWorkspace = AuthContext.runAs("someUser", listOf(role)) {
            workspaceService.mutateWorkspace(
                workspace
            )
        }
        val createdWorkspaceDto = workspaceService.getWorkspace(createdWorkspace)

        assertThat(createdWorkspaceDto).isEqualTo(workspace)

        workspaceService.deleteWorkspace(createdWorkspace)
    }

    @ParameterizedTest
    @ValueSource(strings = [AuthRole.USER, AuthRole.ADMIN, AuthRole.SYSTEM])
    fun `all authenticated users can create private workspaces`(role: String) {
        val workspace = Workspace(
            id = UUID.randomUUID().toString(),
            name = MLText("Test workspace"),
            visibility = WorkspaceVisibility.PRIVATE
        )

        val createdWorkspace = AuthContext.runAs("someUser", listOf(role)) {
            workspaceService.mutateWorkspace(workspace)
        }
        val createdWorkspaceDto = workspaceService.getWorkspace(createdWorkspace)

        assertThat(createdWorkspaceDto).isEqualTo(workspace)

        workspaceService.deleteWorkspace(createdWorkspace)
    }

    @ParameterizedTest
    @ValueSource(strings = [AuthRole.ANONYMOUS, AuthRole.GUEST])
    fun `not authenticated auth should not allow to create workspaces`(role: String) {
        assertThrows<IllegalStateException> {
            AuthContext.runAs("someUser", listOf(role)) {
                workspaceService.mutateWorkspace(
                    Workspace(
                        id = UUID.randomUUID().toString(),
                        name = MLText("Test workspace"),
                        visibility = WorkspaceVisibility.PUBLIC
                    )
                )
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = [AuthRole.ANONYMOUS, AuthRole.GUEST, AuthRole.SYSTEM, AuthRole.ADMIN])
    fun `check blocking auth role join to workspace`(role: String) {
        val created = workspaceService.mutateWorkspace(
            Workspace(
                id = UUID.randomUUID().toString(),
                name = MLText("Test workspace"),
                visibility = WorkspaceVisibility.PUBLIC
            )
        )

        assertThrows<IllegalArgumentException> {
            AuthContext.runAs("someUser", listOf(role)) {
                workspaceService.joinCurrentUserToWorkspace(created)
            }
        }

        workspaceService.deleteWorkspace(created)
    }

    @ParameterizedTest
    @ValueSource(strings = [AuthRole.ANONYMOUS, AuthRole.GUEST, AuthRole.SYSTEM, AuthRole.ADMIN])
    fun `check blocking auth role join to workspace via records`(role: String) {
        val created = workspaceService.mutateWorkspace(
            Workspace(
                id = UUID.randomUUID().toString(),
                name = MLText("Test workspace"),
                visibility = WorkspaceVisibility.PUBLIC
            )
        )

        assertThrows<IllegalArgumentException> {
            AuthContext.runAs("someUser", listOf(role)) {
                val workspaceAtts = RecordAtts(created)
                workspaceAtts[WORKSPACE_ACTION_ATT] = WorkspaceAction.JOIN.name
                recordsService.mutate(workspaceAtts)
            }
        }

        workspaceService.deleteWorkspace(created)
    }

    @ParameterizedTest
    @ValueSource(strings = [AuthUser.SYSTEM, AuthUser.GUEST, AuthUser.ANONYMOUS])
    fun `check blocking auth user join to workspace`(user: String) {
        val created = workspaceService.mutateWorkspace(
            Workspace(
                id = UUID.randomUUID().toString(),
                name = MLText("Test workspace"),
                visibility = WorkspaceVisibility.PUBLIC
            )
        )

        assertThrows<IllegalArgumentException> {
            AuthContext.runAs(user) {
                workspaceService.joinCurrentUserToWorkspace(created)
            }
        }

        workspaceService.deleteWorkspace(created)
    }

    @Test
    fun `user role should allow to join public workspace`() {
        val created = workspaceService.mutateWorkspace(
            Workspace(
                id = UUID.randomUUID().toString(),
                name = MLText("Test workspace"),
                visibility = WorkspaceVisibility.PUBLIC
            )
        )
        val userName = "someUserToJoin"

        AuthContext.runAs(userName, listOf(AuthRole.USER)) {
            workspaceService.joinCurrentUserToWorkspace(created)

            val workspaceInfo = workspaceService.getWorkspace(created)
            val userRef = ecosAuthoritiesApi.getAuthorityRef(userName)
            val userExistsInMembers = workspaceInfo.workspaceMembers.any { it.authority == userRef }

            assertTrue(userExistsInMembers)
        }

        workspaceService.deleteWorkspace(created)
    }

    @Test
    fun `user role should allow to join public workspace via records`() {
        val created = workspaceService.mutateWorkspace(
            Workspace(
                id = UUID.randomUUID().toString(),
                name = MLText("Test workspace"),
                visibility = WorkspaceVisibility.PUBLIC
            )
        )
        val userName = "someUserToJoin"

        AuthContext.runAs(userName, listOf(AuthRole.USER)) {
            val workspaceAtts = RecordAtts(created)
            workspaceAtts[WORKSPACE_ACTION_ATT] = WorkspaceAction.JOIN.name
            recordsService.mutate(workspaceAtts)

            val workspaceInfo = workspaceService.getWorkspace(created)
            val userRef = ecosAuthoritiesApi.getAuthorityRef(userName)
            val userExistsInMembers = workspaceInfo.workspaceMembers.any { it.authority == userRef }

            assertTrue(userExistsInMembers)
        }

        workspaceService.deleteWorkspace(created)
    }

    @ParameterizedTest
    @ValueSource(strings = [AuthRole.ANONYMOUS, AuthRole.GUEST, AuthRole.SYSTEM, AuthRole.ADMIN, AuthRole.USER])
    fun `no one role should not allow to join private workspace`(role: String) {
        val created = workspaceService.mutateWorkspace(
            Workspace(
                id = UUID.randomUUID().toString(),
                name = MLText("Test workspace"),
                visibility = WorkspaceVisibility.PRIVATE
            )
        )

        assertThrows<IllegalArgumentException> {
            AuthContext.runAs("someUser", listOf(role)) {
                workspaceService.joinCurrentUserToWorkspace(created)
            }
        }

        workspaceService.deleteWorkspace(created)
    }

    @Test
    fun `admin user with admin and user roles should allow join to public workspaces`() {
        val created = workspaceService.mutateWorkspace(
            Workspace(
                id = UUID.randomUUID().toString(),
                name = MLText("Test workspace"),
                visibility = WorkspaceVisibility.PUBLIC
            )
        )
        val userName = "someUserToJoin"

        AuthContext.runAs(userName, listOf(AuthRole.ADMIN, AuthRole.USER)) {
            workspaceService.joinCurrentUserToWorkspace(created)

            val workspaceInfo = workspaceService.getWorkspace(created)
            val userRef = ecosAuthoritiesApi.getAuthorityRef(userName)
            val userExistsInMembers = workspaceInfo.workspaceMembers.any { it.authority == userRef }

            assertTrue(userExistsInMembers)
        }

        workspaceService.deleteWorkspace(created)
    }

    @ParameterizedTest
    @ValueSource(strings = [AuthRole.USER, AuthRole.ADMIN, AuthRole.SYSTEM])
    fun `check allowing to create new workspaces`(role: String) {
        val workspace = Workspace(
            id = UUID.randomUUID().toString(),
            name = MLText("Test workspace"),
            visibility = WorkspaceVisibility.PRIVATE
        )

        val createdWorkspace = AuthContext.runAs("someUser", listOf(role)) {
            workspaceService.mutateWorkspace(workspace)
        }
        val createdWorkspaceDto = workspaceService.getWorkspace(createdWorkspace)

        assertThat(createdWorkspaceDto).isEqualTo(workspace)

        workspaceService.deleteWorkspace(createdWorkspace)
    }

    @ParameterizedTest
    @ValueSource(strings = [AuthRole.ANONYMOUS, AuthRole.GUEST])
    fun `check blocking to create new workspaces`(role: String) {
        assertThrows<IllegalStateException> {
            AuthContext.runAs("someUser", listOf(role)) {
                workspaceService.mutateWorkspace(
                    Workspace(
                        id = UUID.randomUUID().toString(),
                        name = MLText("Test workspace"),
                        visibility = WorkspaceVisibility.PRIVATE
                    )
                )
            }
        }
    }

    @ParameterizedTest
    @MethodSource("workspaceVisibilityProvider")
    fun `user should see own created workspace`(visibility: WorkspaceVisibility) {
        val workspace = Workspace(
            id = UUID.randomUUID().toString(),
            name = MLText("Test workspace"),
            visibility = visibility
        )

        val createdWorkspace = AuthContext.runAs("someUser", listOf(AuthRole.USER)) {
            workspaceService.mutateWorkspace(workspace)
        }

        val workspaces = queryWorkspacesFor("someUser", listOf(AuthRole.USER))

        assertThat(workspaces).contains(createdWorkspace)

        workspaceService.deleteWorkspace(createdWorkspace)
    }

    @ParameterizedTest
    @MethodSource("workspaceVisibilityProvider")
    fun `user should allow edit own created workspace`(visibility: WorkspaceVisibility) {
        val workspace = Workspace(
            id = UUID.randomUUID().toString(),
            name = MLText("Test workspace"),
            visibility = visibility
        )

        val createdWorkspace = AuthContext.runAs("someUser", listOf(AuthRole.USER)) {
            workspaceService.mutateWorkspace(workspace)
        }

        AuthContext.runAs("someUser", listOf(AuthRole.USER)) {
            val allowWrite = recordsService.getAtt(createdWorkspace, "permissions._has.Write?bool!").asBoolean()
            assertTrue(allowWrite)
        }

        workspaceService.deleteWorkspace(createdWorkspace)
    }

    fun queryWorkspacesFor(forUser: String, authorities: List<String> = emptyList()): List<EntityRef> {
        return AuthContext.runAs(forUser, authorities) {
            recordsService.query(
                RecordsQuery.create {
                    withSourceId(WORKSPACE_SOURCE_ID)
                    withLanguage(PredicateService.LANGUAGE_PREDICATE)
                    withQuery(Predicates.alwaysTrue())
                    withPage(QueryPage(100, 0, null))
                }
            ).getRecords()
        }
    }
}

private fun String.toWorkspaceRef(): EntityRef {
    return EntityRef.create(AppName.EMODEL, WORKSPACE_SOURCE_ID, this)
}
