package ru.citeck.ecos.model.num

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.model.AuthoritiesHelper
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.domain.workspace.dto.Workspace
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMember
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMemberRole
import ru.citeck.ecos.model.domain.workspace.service.EmodelWorkspaceService
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.model.lib.workspace.IdInWs
import ru.citeck.ecos.model.num.service.NumTemplateService
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension

/**
 * Workspace resolution and permissions of number templates created through the records API.
 *
 * The UI sends '_workspace' either as a local workspace id (records-core default) or as
 * a workspace ref (create button of a SelectJournal field), and in any order relative to 'id'.
 */
@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosModelApp::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NumTemplateRecordsDaoWorkspaceTest {

    companion object {
        private const val SOURCE_ID = "num-template"

        private const val MANAGER = "num-tmpl-ws-manager"
        private const val USER = "num-tmpl-ws-user"

        private const val WS_ID = "num-tmpl-ws"
        private const val WS2_ID = "num-tmpl-ws2"
    }

    @Autowired
    private lateinit var recordsService: RecordsService

    @Autowired
    private lateinit var workspaceService: EmodelWorkspaceService

    @Autowired
    private lateinit var numTemplateService: NumTemplateService

    @Autowired
    private lateinit var authoritiesHelper: AuthoritiesHelper

    private val workspaceRefsToDelete = mutableListOf<EntityRef>()
    private val authorityRefsToDelete = mutableListOf<EntityRef>()

    private var wsId = ""
    private var ws2Id = ""

    @BeforeAll
    fun setUp() {
        AuthContext.runAsSystem {
            authorityRefsToDelete.add(authoritiesHelper.createPerson(MANAGER))
            authorityRefsToDelete.add(authoritiesHelper.createPerson(USER))
            val members = listOf(
                member("m-manager", MANAGER, WorkspaceMemberRole.MANAGER),
                member("m-user", USER, WorkspaceMemberRole.USER)
            )
            wsId = createWorkspace(WS_ID, members)
            ws2Id = createWorkspace(WS2_ID, members)
        }
    }

    @AfterAll
    fun tearDown() {
        AuthContext.runAsSystem {
            recordsService.delete(workspaceRefsToDelete)
            recordsService.delete(authorityRefsToDelete)
        }
    }

    @Test
    fun `manager creates template with id before workspace ref`() {
        val atts = ObjectData.create()
            .set("id", "mgr-id-first")
            .set("name", "mgr-id-first")
            .set("counterKey", "counter")
            .set(RecordConstants.ATT_WORKSPACE, WorkspaceDesc.getRef(wsId).toString())

        val ref = AuthContext.runAs(MANAGER, listOf(AuthRole.USER)) {
            recordsService.create(SOURCE_ID, atts)
        }

        assertStoredInWorkspace(ref, wsId, "mgr-id-first")
    }

    @Test
    fun `manager creates template with workspace ref before id`() {
        val atts = ObjectData.create()
            .set(RecordConstants.ATT_WORKSPACE, WorkspaceDesc.getRef(wsId).toString())
            .set("id", "mgr-ws-first")
            .set("name", "mgr-ws-first")
            .set("counterKey", "counter")

        val ref = AuthContext.runAs(MANAGER, listOf(AuthRole.USER)) {
            recordsService.create(SOURCE_ID, atts)
        }

        assertStoredInWorkspace(ref, wsId, "mgr-ws-first")
    }

    @Test
    fun `manager creates template with local workspace id`() {
        val atts = ObjectData.create()
            .set("id", "mgr-local-ws-id")
            .set("counterKey", "counter")
            .set(RecordConstants.ATT_WORKSPACE, wsId)

        val ref = AuthContext.runAs(MANAGER, listOf(AuthRole.USER)) {
            recordsService.create(SOURCE_ID, atts)
        }

        assertStoredInWorkspace(ref, wsId, "mgr-local-ws-id")
    }

    @Test
    fun `manager creates template with workspace ref in plain workspace attribute`() {
        val atts = ObjectData.create()
            .set("id", "mgr-plain-ws-att")
            .set("counterKey", "counter")
            .set("workspace", WorkspaceDesc.getRef(wsId).toString())

        val ref = AuthContext.runAs(MANAGER, listOf(AuthRole.USER)) {
            recordsService.create(SOURCE_ID, atts)
        }

        assertStoredInWorkspace(ref, wsId, "mgr-plain-ws-att")
    }

    @Test
    fun `non-manager member can't create template in workspace`() {
        for (idFirst in listOf(true, false)) {
            val id = "user-denied-$idFirst"
            val atts = ObjectData.create()
            if (idFirst) {
                atts["id"] = id
            }
            atts[RecordConstants.ATT_WORKSPACE] = WorkspaceDesc.getRef(wsId).toString()
            if (!idFirst) {
                atts["id"] = id
            }
            atts["counterKey"] = "counter"

            assertThatThrownBy {
                AuthContext.runAs(USER, listOf(AuthRole.USER)) {
                    recordsService.create(SOURCE_ID, atts)
                }
            }.hasMessageContaining("Permission denied. You can't create number templates in workspace '$wsId'")

            assertThat(numTemplateService.getByIdOrNull(IdInWs.create(wsId, id))).isNull()
        }
    }

    @Test
    fun `manager can't create template in global scope`() {
        for (ctxWs in listOf("default", WorkspaceDesc.getRef("default").toString(), "admin\$num-tmpl")) {
            val atts = ObjectData.create()
                .set("id", "mgr-global-denied")
                .set("counterKey", "counter")
                .set(RecordConstants.ATT_WORKSPACE, ctxWs)

            assertThatThrownBy {
                AuthContext.runAs(MANAGER, listOf(AuthRole.USER)) {
                    recordsService.create(SOURCE_ID, atts)
                }
            }.hasMessageContaining("Permission denied. You can't create number templates in global scope.")
        }
    }

    @Test
    fun `admin creates template in global scope from workspace with global entities`() {
        val ctxWorkspaces = listOf(
            "default",
            WorkspaceDesc.getRef("default").toString(),
            "admin\$num-tmpl",
            WorkspaceDesc.getRef("admin\$num-tmpl").toString()
        )
        ctxWorkspaces.forEachIndexed { idx, ctxWs ->
            val id = "admin-global-$idx"
            val atts = ObjectData.create()
                .set("id", id)
                .set("counterKey", "counter")
                .set(RecordConstants.ATT_WORKSPACE, ctxWs)

            val ref = AuthContext.runAs("admin", listOf(AuthRole.ADMIN)) {
                recordsService.create(SOURCE_ID, atts)
            }

            assertThat(ref.getLocalId()).describedAs(ctxWs).isEqualTo(id)
            val stored = numTemplateService.getByIdOrNull(IdInWs.create("", id))
            assertThat(stored).describedAs(ctxWs).isNotNull
            // global templates are reported in the default workspace
            assertThat(stored!!.entity.workspace).describedAs(ctxWs).isEqualTo(ModelUtils.DEFAULT_WORKSPACE_ID)
        }
    }

    @Test
    fun `copy of existing template under new id goes to workspace from context`() {
        val sourceRef = AuthContext.runAs(MANAGER, listOf(AuthRole.USER)) {
            recordsService.create(
                SOURCE_ID,
                ObjectData.create()
                    .set("id", "copy-source")
                    .set("counterKey", "counter")
                    .set(RecordConstants.ATT_WORKSPACE, wsId)
            )
        }
        assertStoredInWorkspace(sourceRef, wsId, "copy-source")

        val copyAtts = ObjectData.create()
            .set("id", "copy-target")
            .set(RecordConstants.ATT_WORKSPACE, WorkspaceDesc.getRef(ws2Id).toString())

        val copyRef = AuthContext.runAs(MANAGER, listOf(AuthRole.USER)) {
            recordsService.mutate(sourceRef, copyAtts)
        }

        assertStoredInWorkspace(copyRef, ws2Id, "copy-target")
        assertStoredInWorkspace(sourceRef, wsId, "copy-source")
    }

    private fun assertStoredInWorkspace(ref: EntityRef, workspace: String, id: String) {
        val expectedLocalId = workspaceService.getSystemId(workspace) + IdInWs.WS_DELIM + id
        assertThat(ref.getLocalId()).isEqualTo(expectedLocalId)

        val stored = numTemplateService.getByIdOrNull(IdInWs.create(workspace, id))
        assertThat(stored).isNotNull
        assertThat(stored!!.entity.workspace).isEqualTo(workspace)
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

    private fun member(memberId: String, user: String, role: String) = WorkspaceMember.create()
        .withMemberId(memberId)
        .withAuthorities(listOf(AuthorityType.PERSON.getRef(user)))
        .withMemberRole(role)
        .build()
}
