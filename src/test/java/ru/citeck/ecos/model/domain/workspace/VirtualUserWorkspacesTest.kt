package ru.citeck.ecos.model.domain.workspace

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.apps.app.service.LocalAppService
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.utils.resource.ResourceUtils
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.model.domain.workspace.WorkspacePermissionsTest.Companion.GRYFFINDOR_WORKSPACE
import ru.citeck.ecos.model.domain.workspace.api.records.WorkspaceProxyDao
import ru.citeck.ecos.model.domain.workspace.api.records.WorkspaceProxyDao.Companion.WORKSPACE_SOURCE_ID
import ru.citeck.ecos.model.domain.workspace.dto.Workspace
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMember
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMemberRole
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceVisibility
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.model.lib.workspace.USER_WORKSPACE_PREFIX
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.query.dto.query.QueryPage
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.entity.toEntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import kotlin.test.Test

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosModelApp::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VirtualUserWorkspacesTest {

    @Autowired
    private lateinit var recordsService: RecordsService

    @Autowired
    private lateinit var localAppService: LocalAppService

    companion object {
        private val ronPersonalWorkspace = Workspace(
            id = "${USER_WORKSPACE_PREFIX}ron",
            name = MLText(
                I18nContext.ENGLISH to "Personal workspace",
                I18nContext.RUSSIAN to "Персональное рабочее пространство"
            ),
            visibility = WorkspaceVisibility.PRIVATE,
            workspaceMembers = listOf(
                WorkspaceMember(
                    id = "ron",
                    authority = AuthorityType.PERSON.getRef("ron"),
                    memberRole = WorkspaceMemberRole.MANAGER
                )
            )
        )

        private val harryPersonalWorkspaceDto = Workspace(
            id = "${USER_WORKSPACE_PREFIX}harry",
            name = MLText(
                I18nContext.ENGLISH to "Personal workspace",
                I18nContext.RUSSIAN to "Персональное рабочее пространство"
            ),
            visibility = WorkspaceVisibility.PRIVATE,
            workspaceMembers = listOf(
                WorkspaceMember(
                    id = "harry",
                    authority = AuthorityType.PERSON.getRef("harry"),
                    memberRole = WorkspaceMemberRole.MANAGER
                )
            )
        )

        private val gryffindorWorkspaceDto = Workspace(
            id = GRYFFINDOR_WORKSPACE,
            name = MLText(
                I18nContext.ENGLISH to "Gryffindor",
                I18nContext.RUSSIAN to "Гриффиндор"
            ),
            description = MLText(
                I18nContext.ENGLISH to "The workspace for Gryffindor",
                I18nContext.RUSSIAN to "Рабочее пространство для Гриффиндор"
            ),
            visibility = WorkspaceVisibility.PRIVATE,
            workspaceMembers = listOf(
                WorkspaceMember(
                    id = "gryffindor-member-harry",
                    authority = "emodel/person@harry".toEntityRef(),
                    memberRole = WorkspaceMemberRole.MANAGER
                ),
                WorkspaceMember(
                    id = "gryffindor-member-ron",
                    authority = "emodel/person@ron".toEntityRef(),
                    memberRole = WorkspaceMemberRole.USER
                ),
                WorkspaceMember(
                    id = "gryffindor-manager-group",
                    authority = "emodel/authority-group@gryffindor-managers".toEntityRef(),
                    memberRole = WorkspaceMemberRole.MANAGER
                )
            )
        )
    }

    @BeforeAll
    fun setUp() {
        AuthContext.runAsSystem {
            localAppService.deployLocalArtifacts(ResourceUtils.getFile("classpath:eapps/artifacts"))
        }
    }

    @Test
    fun `query workspaces with user workspace meta data test`() {
        val ronUser = "ron"
        val workspaces = AuthContext.runAs("ron", listOf(AuthRole.USER)) {
            queryUserWorkspaces(ronUser)
        }

        assertThat(workspaces).containsExactlyElementsOf(
            listOf(
                gryffindorWorkspaceDto,
                ronPersonalWorkspace
            )
        )
    }

    @ParameterizedTest
    @ValueSource(strings = [AuthRole.ADMIN, AuthRole.SYSTEM])
    fun `admin and system roles should see personal workspaces atts of all users`(role: String) {
        val workspaces = AuthContext.runAs("someUser", listOf(role)) {
            recordsService.getAtts(
                listOf(
                    "ron".toUsernameToUserVirtualWorkspaceRef(),
                    "harry".toUsernameToUserVirtualWorkspaceRef()
                ),
                Workspace::class.java
            )
        }

        assertThat(workspaces).containsExactlyElementsOf(
            listOf(ronPersonalWorkspace, harryPersonalWorkspaceDto)
        )
    }

    @ParameterizedTest
    @ValueSource(strings = [AuthRole.ANONYMOUS, AuthRole.GUEST])
    fun `not authorized user should not see personal workspaces atts`(role: String) {
        val workspaces = AuthContext.runAs("someUser", listOf(role)) {
            recordsService.getAtts(
                listOf(
                    "ron".toUsernameToUserVirtualWorkspaceRef(),
                    "harry".toUsernameToUserVirtualWorkspaceRef()
                ),
                Workspace::class.java
            )
        }

        assertThat(workspaces).containsExactlyElementsOf(
            listOf(generateEmptyAttValueWorkspace("ron"), generateEmptyAttValueWorkspace("harry"))
        )
    }

    @Test
    fun `user should not see personal workspaces atts of another user`() {
        val workspaces = AuthContext.runAs("ron", listOf(AuthRole.USER)) {
            recordsService.getAtts(
                listOf(
                    GRYFFINDOR_WORKSPACE.toWorkspaceRef(),
                    "ron".toUsernameToUserVirtualWorkspaceRef(),
                    "harry".toUsernameToUserVirtualWorkspaceRef()
                ),
                Workspace::class.java
            )
        }

        assertThat(workspaces).containsExactlyElementsOf(
            listOf(gryffindorWorkspaceDto, ronPersonalWorkspace, generateEmptyAttValueWorkspace("harry"))
        )
    }

    private fun generateEmptyAttValueWorkspace(user: String): Workspace {
        return Workspace(
            id = "${USER_WORKSPACE_PREFIX}$user",
            name = MLText.EMPTY,
            visibility = WorkspaceVisibility.PRIVATE
        )
    }

    private fun queryUserWorkspaces(user: String): List<Workspace> {
        return recordsService.query(
            RecordsQuery.create {
                withSourceId(WORKSPACE_SOURCE_ID)
                withLanguage(WorkspaceProxyDao.USER_WORKSPACES)
                withQuery(
                    DataValue.of(
                        """
                        {
                            "user": "$user"
                        }
                        """.trimIndent()
                    )
                )
                withPage(QueryPage(100, 0, null))
            },
            Workspace::class.java
        ).getRecords()
    }
}
