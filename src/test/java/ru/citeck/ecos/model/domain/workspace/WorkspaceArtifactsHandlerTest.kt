package ru.citeck.ecos.model.domain.workspace

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.apps.app.service.LocalAppService
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.io.file.std.EcosStdFile
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.commons.utils.resource.ResourceUtils
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.model.domain.workspace.api.records.WorkspaceProxyDao.Companion.WORKSPACE_SOURCE_ID
import ru.citeck.ecos.model.domain.workspace.dto.Workspace
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMember
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMemberRole
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceVisibility
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosModelApp::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkspaceArtifactsHandlerTest {

    @Autowired
    private lateinit var recordsService: RecordsService

    @Autowired
    private lateinit var localAppService: LocalAppService

    @BeforeAll
    fun setUp() {
        AuthContext.runAsSystem {
            localAppService.deployLocalArtifacts(ResourceUtils.getFile("classpath:eapps/artifacts"))
        }
    }

    @Test
    fun `check workspace meta data after deploy`() {
        val ref = EntityRef.create(
            AppName.EMODEL,
            WORKSPACE_SOURCE_ID,
            "test-workspace-artifact"
        )
        val workspace = recordsService.getAtts(
            ref,
            Workspace::class.java
        )

        assertThat(workspace).isNotNull
        assertThat(workspace.name).isEqualTo(
            MLText(
                I18nContext.ENGLISH to "Test workspace artifact",
                I18nContext.RUSSIAN to "Тестовый артефакт рабочего пространства"
            )
        )
        assertThat(workspace.description).isEqualTo(
            MLText(
                I18nContext.ENGLISH to "This is a test workspace artifact",
                I18nContext.RUSSIAN to "Это тестовый артефакт рабочего пространства"
            )
        )
        assertThat(workspace.visibility).isEqualTo(WorkspaceVisibility.PUBLIC)
        assertThat(workspace.workspaceMembers).hasSize(2)
        assertThat(workspace.workspaceMembers).containsExactlyInAnyOrder(
            WorkspaceMember.create {
                id = "test-workspace-member-user-artifact-child"
                authority = EntityRef.create(AppName.EMODEL, "person", "test-user")
                memberRole = WorkspaceMemberRole.MANAGER
            },
            WorkspaceMember.create {
                id = "test-workspace-member-group-artifact-child"
                authority = EntityRef.create(AppName.EMODEL, "authority-group", "test-group")
                memberRole = WorkspaceMemberRole.USER
            }
        )
    }

    @Test
    fun `check json attribute`() {
        val ref = EntityRef.create(
            AppName.EMODEL,
            WORKSPACE_SOURCE_ID,
            "test-workspace-artifact"
        )

        val expectedData = Json.mapper.readNotNull(
            EcosStdFile(
                ResourceUtils.getFile(
                    "classpath:eapps/artifacts/model/workspace/test-workspace-artifact.yml"
                )
            ),
            DataValue::class.java
        )
        val workspaceJson = recordsService.getAtt(ref, "?json")
        assertThat(workspaceJson.getAs(Workspace::class.java))
            .isEqualTo(expectedData.getAs(Workspace::class.java))
    }
}
