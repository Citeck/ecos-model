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
import ru.citeck.ecos.commons.utils.resource.ResourceUtils
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.model.AuthoritiesHelper
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceMemberDesc
import ru.citeck.ecos.model.domain.workspace.dto.Workspace
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMemberRole
import ru.citeck.ecos.model.domain.workspace.listener.WorkspaceRecordsListener
import ru.citeck.ecos.model.domain.workspace.service.EmodelWorkspaceService
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.model.lib.type.dto.QueryPermsPolicy
import ru.citeck.ecos.model.lib.type.dto.WorkspaceScope
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.model.type.service.TypesService
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosModelApp::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkspaceManagedByVisibilityTest {

    companion object {
        private const val PROJECTS_SOURCE_ID = "test-managed-projects"
        private const val PROJECTS_TYPE_ID = "test-managed-project"
        private const val WS_PROJECT = "ws-project"
        private const val WS_MANAGING = "ws-managing"
        private const val USER = "visibility-test-user"
    }

    @Autowired
    private lateinit var recordsService: RecordsService

    @Autowired
    private lateinit var localAppService: LocalAppService

    @Autowired
    private lateinit var workspaceService: EmodelWorkspaceService

    @Autowired
    private lateinit var dbDomainFactory: DbDomainFactory

    @Autowired
    private lateinit var typesService: TypesService

    @Autowired
    private lateinit var authoritiesHelper: AuthoritiesHelper

    private val refsToDelete = mutableListOf<EntityRef>()

    @BeforeAll
    fun setUp() {
        localAppService.deployLocalArtifacts(ResourceUtils.getFile("classpath:eapps/artifacts"))

        refsToDelete.add(authoritiesHelper.createPerson(USER))

        typesService.save(
            TypeDef.create()
                .withId(PROJECTS_TYPE_ID)
                .withSourceId(PROJECTS_SOURCE_ID)
                .withWorkspaceScope(WorkspaceScope.PRIVATE)
                .withDefaultWorkspace(WS_PROJECT)
                .withQueryPermsPolicy(QueryPermsPolicy.OWN)
                .build()
        )

        val projectsDao = dbDomainFactory.create(
            DbDomainConfig.create()
                .withRecordsDao(
                    DbRecordsDaoConfig.create {
                        withId(PROJECTS_SOURCE_ID)
                        withTypeRef(ModelUtils.getTypeRef(PROJECTS_TYPE_ID))
                    }
                )
                .withDataService(
                    DbDataServiceConfig.create {
                        withTable("test_managed_projects")
                        withStoreTableMeta(true)
                    }
                ).build()
        ).withSchema("ecos_data").build()

        recordsService.register(projectsDao)
    }

    @AfterAll
    fun tearDown() {
        AuthContext.runAsSystem {
            for (ref in refsToDelete.reversed()) {
                try {
                    recordsService.delete(ref)
                } catch (_: Exception) {
                    // ignore cleanup errors
                }
            }
        }
        recordsService.unregister(PROJECTS_SOURCE_ID)
    }

    /**
     * Scenario:
     * - Entity B (project) lives in workspace C (ws-project)
     * - Workspace A (ws-managing) has workspaceManagedBy = B
     * - User is member of workspace A but NOT member of workspace C
     *
     * Expected: user can read entity B
     */
    @Test
    fun memberOfManagingWorkspaceCanAccessManagedEntity() {

        // Create entity B in workspace C
        val projectRef = AuthContext.runAsSystem {
            val ref = recordsService.create(
                PROJECTS_SOURCE_ID,
                mapOf(
                    "name" to "secret-project",
                    RecordConstants.ATT_WORKSPACE to WS_PROJECT
                )
            )
            refsToDelete.add(ref)
            ref
        }

        // Create workspace A and set workspaceManagedBy = entity B
        val wsRef = AuthContext.runAsSystem {
            workspaceService.deployWorkspace(
                Workspace.create()
                    .withId(WS_MANAGING)
                    .withName(MLText(WS_MANAGING))
                    .build()
            )
            val ref = WorkspaceDesc.getRef(WS_MANAGING)
            refsToDelete.add(ref)
            recordsService.mutateAtt(ref, WorkspaceRecordsListener.ATT_WORKSPACE_MANAGED_BY, projectRef)
            ref
        }

        // Add user to workspace A as member
        AuthContext.runAsSystem {
            refsToDelete.add(
                recordsService.create(
                    WorkspaceMemberDesc.SOURCE_ID,
                    mapOf(
                        RecordConstants.ATT_PARENT to wsRef,
                        RecordConstants.ATT_PARENT_ATT to WorkspaceDesc.ATT_WORKSPACE_MEMBERS,
                        WorkspaceMemberDesc.ATT_AUTHORITIES to AuthorityType.PERSON.getRef(USER),
                        WorkspaceMemberDesc.ATT_MEMBER_ROLE to WorkspaceMemberRole.USER
                    )
                )
            )
        }

        // User is NOT in ws-project, only in ws-managing -> should still see the project
        AuthContext.runAsFull(USER, listOf(AuthRole.USER)) {
            val result = recordsService.query(
                RecordsQuery.create()
                    .withSourceId(PROJECTS_SOURCE_ID)
                    .withQuery(Predicates.alwaysTrue())
                    .withWorkspaces(listOf(WS_MANAGING))
                    .build()
            ).getRecords()
            assertThat(result).contains(projectRef)
        }
    }

    @Test
    fun nonMemberCannotAccessManagedEntity() {

        val projectRef = AuthContext.runAsSystem {
            val ref = recordsService.create(
                PROJECTS_SOURCE_ID,
                mapOf(
                    "name" to "another-project",
                    RecordConstants.ATT_WORKSPACE to WS_PROJECT
                )
            )
            refsToDelete.add(ref)
            ref
        }

        AuthContext.runAsSystem {
            workspaceService.deployWorkspace(
                Workspace.create()
                    .withId("ws-managing-2")
                    .withName(MLText("ws-managing-2"))
                    .build()
            )
            val ref = WorkspaceDesc.getRef("ws-managing-2")
            refsToDelete.add(ref)
            recordsService.mutateAtt(ref, WorkspaceRecordsListener.ATT_WORKSPACE_MANAGED_BY, projectRef)
        }

        // outsider is NOT a member of ws-managing-2 and NOT a member of ws-project
        // -> project should NOT appear in query results
        AuthContext.runAsFull("outsider", listOf(AuthRole.USER)) {
            val result = recordsService.query(
                RecordsQuery.create()
                    .withSourceId(PROJECTS_SOURCE_ID)
                    .withQuery(Predicates.alwaysTrue())
                    .withWorkspaces(listOf("ws-managing-2"))
                    .build()
            ).getRecords()
            assertThat(result).doesNotContain(projectRef)
        }
    }
}
