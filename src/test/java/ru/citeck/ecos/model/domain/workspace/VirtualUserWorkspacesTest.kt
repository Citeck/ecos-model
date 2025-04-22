package ru.citeck.ecos.model.domain.workspace

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
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
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.model.TestNotificationService
import ru.citeck.ecos.model.domain.workspace.WorkspacePermissionsTest.Companion.GRYFFINDOR_WORKSPACE
import ru.citeck.ecos.model.domain.workspace.api.records.WorkspaceProxyDao
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.domain.workspace.dto.Workspace
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMember
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMemberRole
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceVisibility
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.model.lib.workspace.USER_WORKSPACE_PREFIX
import ru.citeck.ecos.notifications.lib.service.NotificationService
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.dao.query.dto.query.QueryPage
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.entity.EntityRef
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

    @Autowired
    private lateinit var notificationService: NotificationService

    companion object {
        private val personalWsIconRef = EntityRef.valueOf("uiserv/icon@personal-workspace-icon")

        private val defaultWorkspace = Workspace(
            id = "default",
            name = MLText(
                I18nContext.RUSSIAN to "По умолчанию",
                I18nContext.ENGLISH to "Default"
            ),
            visibility = WorkspaceVisibility.PUBLIC,
            workspaceMembers = listOf(
                WorkspaceMember(
                    memberId = "default-administrators",
                    authorities = listOf(AuthorityType.GROUP.getRef("ECOS_ADMINISTRATORS")),
                    memberRole = WorkspaceMemberRole.MANAGER
                ),
                WorkspaceMember(
                    memberId = "default-all-users",
                    authorities = listOf(AuthorityType.GROUP.getRef("EVERYONE")),
                    memberRole = WorkspaceMemberRole.USER
                )
            ),
            homePageLink = "",
            icon = EntityRef.EMPTY,
            system = false
        )

        private val ronPersonalWorkspace = Workspace(
            id = "${USER_WORKSPACE_PREFIX}ron",
            name = MLText(
                I18nContext.ENGLISH to "Personal workspace",
                I18nContext.RUSSIAN to "Персональное рабочее пространство"
            ),
            visibility = WorkspaceVisibility.PRIVATE,
            workspaceMembers = listOf(
                WorkspaceMember(
                    memberId = "ron",
                    authorities = listOf(AuthorityType.PERSON.getRef("ron")),
                    memberRole = WorkspaceMemberRole.MANAGER
                )
            ),
            homePageLink = "",
            icon = personalWsIconRef,
            system = false
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
                    memberId = "harry",
                    authorities = listOf(AuthorityType.PERSON.getRef("harry")),
                    memberRole = WorkspaceMemberRole.MANAGER
                )
            ),
            homePageLink = "",
            icon = personalWsIconRef,
            system = false
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
                    memberId = "gryffindor-member-harry",
                    authorities = listOf("emodel/person@harry".toEntityRef()),
                    memberRole = WorkspaceMemberRole.MANAGER
                ),
                WorkspaceMember(
                    memberId = "gryffindor-member-ron",
                    authorities = listOf("emodel/person@ron".toEntityRef()),
                    memberRole = WorkspaceMemberRole.USER
                ),
                WorkspaceMember(
                    memberId = "gryffindor-manager-group",
                    authorities = listOf("emodel/authority-group@gryffindor-managers".toEntityRef()),
                    memberRole = WorkspaceMemberRole.MANAGER
                )
            ),
            homePageLink = "",
            icon = EntityRef.EMPTY,
            system = false
        )
    }

    @BeforeAll
    fun setUp() {
        AuthContext.runAsSystem {
            localAppService.deployLocalArtifacts("model/workspace")
        }
    }

    @AfterAll
    fun tearDown() {
        AuthContext.runAsSystem {
            recordsService.delete(
                recordsService.query(
                    RecordsQuery.create()
                        .withSourceId(WorkspaceDesc.SOURCE_ID)
                        .withQuery(
                            Predicates.not(
                                Predicates.inVals(ScalarType.LOCAL_ID.mirrorAtt, WorkspaceProxyDao.UNDELETABLE_WORKSPACES)
                            )
                        ).withMaxItems(10000)
                        .build()
                ).getRecords()
            )
        }

        (notificationService as? TestNotificationService.NotificationServiceTestImpl)?.cleanNotificationStorage()
    }

    @Test
    fun `query workspaces with user workspace meta data test`() {
        val ronUser = "ron"
        val workspaces = AuthContext.runAs("ron", listOf(AuthRole.USER)) {
            queryUserWorkspaces(ronUser)
        }

        assertThat(workspaces).containsExactlyInAnyOrderElementsOf(
            listOf(
                gryffindorWorkspaceDto,
                ronPersonalWorkspace,
                defaultWorkspace
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
            visibility = WorkspaceVisibility.PRIVATE,
            homePageLink = "",
            icon = EntityRef.EMPTY,
            system = false
        )
    }

    private fun queryUserWorkspaces(user: String): List<Workspace> {
        return recordsService.query(
            RecordsQuery.create {
                withSourceId(WorkspaceDesc.SOURCE_ID)
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
