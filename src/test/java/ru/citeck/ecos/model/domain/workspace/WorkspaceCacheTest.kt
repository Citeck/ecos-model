package ru.citeck.ecos.model.domain.workspace

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ConfigurableApplicationContext
import ru.citeck.ecos.apps.app.service.LocalAppService
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.context.lib.auth.data.SimpleAuthData
import ru.citeck.ecos.model.AuthoritiesHelper
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.model.domain.workspace.api.records.WorkspaceProxyDao.Companion.WORKSPACE_REPO_SOURCE_ID
import ru.citeck.ecos.model.domain.workspace.config.WorkspaceIdMappingSourcesRegistrar
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceMemberDesc
import ru.citeck.ecos.model.domain.workspace.dto.Workspace
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMember
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMemberRole
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceVisibility
import ru.citeck.ecos.model.domain.workspace.service.CustomWorkspaceApi
import ru.citeck.ecos.model.domain.workspace.service.EmodelWorkspaceService
import ru.citeck.ecos.model.domain.workspace.utils.WorkspaceSystemIdUtils
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.model.lib.workspace.USER_WORKSPACE_PREFIX
import ru.citeck.ecos.model.lib.workspace.api.WsMembershipType
import ru.citeck.ecos.model.num.service.NumRegistryInitializer
import ru.citeck.ecos.model.type.service.TypesRegistryInitializer
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

    @Autowired
    private lateinit var applicationContext: ConfigurableApplicationContext

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

    /**
     * COREDEV-514: a lookup made before the workspace exists must not hide it afterwards.
     * The unresolved mapping is cached, and until it is dropped every artifact of the workspace
     * is read as non-existent, because the workspace prefix of its ref stays unresolved.
     */
    @Test
    fun workspaceIdMappingsAreEvictedWhenWorkspaceAppears() {

        val wsId = "cache-test-ws-mapping"
        val wsSysId = WorkspaceSystemIdUtils.createId(wsId)

        val idBefore = AuthContext.runAsSystem { workspaceService.getWorkspaceIdBySystemId(wsSysId) }
        assertThat(idBefore).isEmpty()

        val sysIdBefore = AuthContext.runAsSystem { workspaceService.getSystemId(wsId) }
        assertThat(sysIdBefore).startsWith(EmodelWorkspaceService.DELETED_WS_SYS_ID_PREFIX)

        createWorkspace(wsId, listOf(managerMember("m0", AuthorityType.PERSON.getRef(USER_B))))

        val idAfter = AuthContext.runAsSystem { workspaceService.getWorkspaceIdBySystemId(wsSysId) }
        assertThat(idAfter).isEqualTo(wsId)

        val sysIdAfter = AuthContext.runAsSystem { workspaceService.getSystemId(wsId) }
        assertThat(sysIdAfter).isEqualTo(wsSysId)
    }

    /**
     * COREDEV-514: a query to a records source which is not registered returns an empty result
     * without any error, and such an answer must not become a cached "workspace is not found".
     * The check must recognize the sources actually used for the lookups.
     */
    @Test
    fun lookupInUnregisteredRecordsSourceFails() {

        assertThatThrownBy {
            workspaceService.checkSourceIsRegistered("no-such-source-coredev-514", "test lookup")
        }.hasMessageContaining("is not registered")

        assertThatCode {
            workspaceService.checkSourceIsRegistered(WorkspaceDesc.SOURCE_ID, "test lookup")
            workspaceService.checkSourceIsRegistered(AuthorityType.PERSON.sourceId, "test lookup")
        }.doesNotThrowAnyException()
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

    /**
     * COREDEV-550: a lookup made while its records source is not registered yet must neither fail
     * the caller (it killed the whole start) nor cache its answer - the next lookup after the
     * registration resolves the workspace at once, without waiting for the negative TTL.
     */
    @Test
    fun lookupWhileSourceIsNotRegisteredDoesNotFailAndIsNotCached() {

        val wsId = createWorkspace(
            "cache-test-ws-startup-window",
            listOf(managerMember("m0", AuthorityType.PERSON.getRef(USER_B)))
        )
        val wsSysId = AuthContext.runAsSystem { workspaceService.getSystemId(wsId) }
        assertThat(wsSysId).doesNotStartWith(EmodelWorkspaceService.DELETED_WS_SYS_ID_PREFIX)
        workspaceService.evictIdMappings(wsId, wsSysId)

        val workspaceDao = recordsService.getRecordsDao(WorkspaceDesc.SOURCE_ID)
            ?: error("Records source '${WorkspaceDesc.SOURCE_ID}' is not registered")
        recordsService.unregister(WorkspaceDesc.SOURCE_ID)
        try {
            val sysIdInWindow = AuthContext.runAsSystem { workspaceService.getSystemId(wsId) }
            assertThat(sysIdInWindow).startsWith(EmodelWorkspaceService.DELETED_WS_SYS_ID_PREFIX)
            val idInWindow = AuthContext.runAsSystem { workspaceService.getWorkspaceIdBySystemId(wsSysId) }
            assertThat(idInWindow).isEmpty()
        } finally {
            recordsService.register(workspaceDao)
        }

        val sysIdAfter = AuthContext.runAsSystem { workspaceService.getSystemId(wsId) }
        assertThat(sysIdAfter).isEqualTo(wsSysId)
        val idAfter = AuthContext.runAsSystem { workspaceService.getWorkspaceIdBySystemId(wsSysId) }
        assertThat(idAfter).isEqualTo(wsId)
    }

    /**
     * COREDEV-550, the person branch: the system id of a personal workspace is mapped back through
     * the 'person' source, and a lookup in the startup window follows the same contract.
     */
    @Test
    fun userWorkspaceLookupWhilePersonSourceIsNotRegisteredDoesNotFailAndIsNotCached() {

        val userWsSysId = WorkspaceSystemIdUtils.USER_WS_SYS_ID_PREFIX + WorkspaceSystemIdUtils.createId(USER_B)

        val personDao = recordsService.getRecordsDao(AuthorityType.PERSON.sourceId)
            ?: error("Records source '${AuthorityType.PERSON.sourceId}' is not registered")
        recordsService.unregister(AuthorityType.PERSON.sourceId)
        try {
            val idInWindow = AuthContext.runAsSystem { workspaceService.getWorkspaceIdBySystemId(userWsSysId) }
            assertThat(idInWindow).isEmpty()
        } finally {
            recordsService.register(personDao)
        }

        val idAfter = AuthContext.runAsSystem { workspaceService.getWorkspaceIdBySystemId(userWsSysId) }
        assertThat(idAfter).startsWith(USER_WORKSPACE_PREFIX)
    }

    /**
     * COREDEV-550: the mapping reads 'workspace' through a proxy. A registered proxy whose target
     * ('workspace-repo') is not registered yet answers with an empty result as well, so the guard
     * must recognize that state too - otherwise the empty answer is cached as "not found".
     */
    @Test
    fun lookupWhileProxyTargetIsNotRegisteredDoesNotFailAndIsNotCached() {

        val wsId = createWorkspace(
            "cache-test-ws-proxy-target",
            listOf(managerMember("m0", AuthorityType.PERSON.getRef(USER_B)))
        )
        val wsSysId = AuthContext.runAsSystem { workspaceService.getSystemId(wsId) }
        assertThat(wsSysId).doesNotStartWith(EmodelWorkspaceService.DELETED_WS_SYS_ID_PREFIX)
        workspaceService.evictIdMappings(wsId, wsSysId)

        val repoDao = recordsService.getRecordsDao(WORKSPACE_REPO_SOURCE_ID)
            ?: error("Records source '$WORKSPACE_REPO_SOURCE_ID' is not registered")
        recordsService.unregister(WORKSPACE_REPO_SOURCE_ID)
        try {
            assertThat(recordsService.getRecordsDao(WorkspaceDesc.SOURCE_ID)).describedAs("proxy stays").isNotNull
            val sysIdInWindow = AuthContext.runAsSystem { workspaceService.getSystemId(wsId) }
            assertThat(sysIdInWindow).startsWith(EmodelWorkspaceService.DELETED_WS_SYS_ID_PREFIX)
            val idInWindow = AuthContext.runAsSystem { workspaceService.getWorkspaceIdBySystemId(wsSysId) }
            assertThat(idInWindow).isEmpty()
        } finally {
            recordsService.register(repoDao)
        }

        assertThat(AuthContext.runAsSystem { workspaceService.getSystemId(wsId) }).isEqualTo(wsSysId)
        assertThat(AuthContext.runAsSystem { workspaceService.getWorkspaceIdBySystemId(wsSysId) }).isEqualTo(wsId)
    }

    /**
     * COREDEV-550: the registries which map workspace ids at startup must be initialized after the
     * sources of the mapping are registered. The ordering is declared with @DependsOn and nothing
     * else enforces it, so the declaration itself is guarded here.
     */
    @Test
    fun registryInitializersDependOnMappingSourcesRegistrar() {
        val beanFactory = applicationContext.beanFactory
        for (type in listOf(TypesRegistryInitializer::class.java, NumRegistryInitializer::class.java)) {
            val beanName = beanFactory.getBeanNamesForType(type).single()
            val dependsOn = beanFactory.getBeanDefinition(beanName).dependsOn ?: emptyArray()
            assertThat(dependsOn).describedAs(beanName).contains(WorkspaceIdMappingSourcesRegistrar.BEAN_NAME)
        }
    }
}
