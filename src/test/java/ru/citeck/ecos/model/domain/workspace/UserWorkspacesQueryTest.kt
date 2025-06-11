package ru.citeck.ecos.model.domain.workspace

import org.assertj.core.api.Assertions.assertThat
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
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.domain.workspace.dto.Workspace
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMember
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMemberRole
import ru.citeck.ecos.model.domain.workspace.service.EmodelWorkspaceService
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosModelApp::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserWorkspacesQueryTest {

    @Autowired
    private lateinit var recordsService: RecordsService

    @Autowired
    private lateinit var localAppService: LocalAppService

    @Autowired
    private lateinit var workspaceService: EmodelWorkspaceService

    @BeforeAll
    fun setUp() {
        AuthContext.runAsSystem {
            localAppService.deployLocalArtifacts("model/workspace")
        }
    }

    @Test
    fun testPredicateQuery() {
        val workspacesIds = (0..9).map {
            val wsId = workspaceService.deployWorkspace(
                Workspace.create().withId("test-ws-$it")
                    .withName(MLText("test-ws-$it"))
                    .withWorkspaceMembers(
                        listOf(
                            WorkspaceMember.create()
                                .withMemberId("member-0")
                                .withAuthorities(listOf(AuthorityType.PERSON.getRef("user-0")))
                                .withMemberRole(WorkspaceMemberRole.MANAGER)
                                .build()
                        )
                    ).build()
            )
            Thread.sleep(10)
            wsId
        }
        fun assertQuery(condition: Predicate, max: Int, skip: Int, expectedWorkspaces: List<String>, expectedTotalCount: Int) {
            val queryRes = recordsService.query(RecordsQuery.create()
                .withSourceId("workspace")
                .withMaxItems(max)
                .withSkipCount(skip)
                .withQuery(condition)
                .withSortBy(SortBy("_created", true))
                .build()
            )
            val recs = queryRes.getRecords().map { it.getLocalId() }
            assertThat(recs).containsExactlyInAnyOrderElementsOf(expectedWorkspaces)
            assertThat(queryRes.getTotalCount().toInt()).isEqualTo(expectedTotalCount)
        }
        val currentUserMemberPredicate = Predicates.eq(WorkspaceDesc.ATT_IS_CURRENT_USER_MEMBER, true)

        AuthContext.runAs("user-0", listOf(AuthRole.USER)) {
            assertQuery(
                currentUserMemberPredicate,
                100, 0,
                listOf("user\$user-0", *workspacesIds.toTypedArray()),
                11
            )
            assertQuery(
                currentUserMemberPredicate,
                2, 0,
                listOf("user\$user-0", workspacesIds.first()),
                11
            )
            assertQuery(
                currentUserMemberPredicate,
                2, 2,
                listOf(workspacesIds[1], workspacesIds[2]),
                11
            )
            assertQuery(
                currentUserMemberPredicate,
                2, 4,
                listOf(workspacesIds[3], workspacesIds[4]),
                11
            )
            assertQuery(
                currentUserMemberPredicate,
                4, 10,
                listOf(workspacesIds[9]),
                11
            )
            assertQuery(
                Predicates.and(currentUserMemberPredicate, Predicates.contains("name", "перс")),
                4, 0,
                listOf("user\$user-0"),
                1
            )
            assertQuery(
                Predicates.and(currentUserMemberPredicate, Predicates.contains("name", "test-ws")),
                4, 0,
                workspacesIds.take(4),
                10
            )
            assertQuery(
                Predicates.and(currentUserMemberPredicate, Predicates.contains("name", "test-ws")),
                10, 10,
                emptyList(),
                10
            )
        }
        workspacesIds.forEach { workspaceService.deleteWorkspace(it) }
    }
}
