package ru.citeck.ecos.model.domain.workspace.service

import com.github.benmanes.caffeine.cache.Caffeine
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import ru.citeck.ecos.model.lib.ModelServiceFactory
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.model.lib.workspace.api.WorkspaceApi
import ru.citeck.ecos.model.lib.workspace.api.WsMembershipType
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.time.Duration
import java.util.Optional

@Primary
@Component
class CustomWorkspaceApi(
    private val workspaceService: EmodelWorkspaceService,
    private val modelServiceFactory: ModelServiceFactory
) : WorkspaceApi {

    private val userWorkspacesCache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(30))
        .maximumSize(1000)
        .build<UserWorkspacesCacheKey, Set<String>> { key ->
            workspaceService.getUserWorkspaces(
                key.user,
                key.membershipType,
                sortByVisits = false
            ).workspaces
        }

    private val isUserManagerOfCache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(30))
        .maximumSize(1000)
        .build<IsUserManagerOfKey, Boolean> { key ->
            workspaceService.isUserManagerOf(key.user, key.workspace)
        }

    private val workspaceManagersRefsCache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(30))
        .maximumSize(1000)
        .build<String, Optional<Set<EntityRef>>> { workspaceId ->
            Optional.ofNullable(workspaceService.getWorkspaceManagersRefs(workspaceId))
        }

    @PostConstruct
    fun init() {
        modelServiceFactory.setWorkspaceApi(this)
        workspaceService.addJoinListener { userId, _ -> evictUserWorkspaces(userId) }
        workspaceService.addLeaveListener { userId, workspaceId ->
            evictUserWorkspaces(userId)
            evictIsUserManagerOfForWorkspace(workspaceId)
            evictWorkspaceManagersRefs(workspaceId)
        }
    }

    override fun getNestedWorkspaces(workspaces: Collection<String>): List<Set<String>> {
        return workspaceService.getNestedWorkspaces(workspaces)
    }

    override fun getUserWorkspaces(user: String, membershipType: WsMembershipType): Set<String> {
        return userWorkspacesCache.get(UserWorkspacesCacheKey(user, membershipType))
    }

    override fun isUserManagerOf(user: String, workspace: String): Boolean {
        return isUserManagerOfCache.get(IsUserManagerOfKey(user, workspace))
    }

    fun getWorkspaceManagersRefs(workspaceId: String): Set<EntityRef>? {
        return workspaceManagersRefsCache.get(workspaceId).orElse(null)
    }

    override fun mapIdentifiers(identifiers: List<String>, mappingType: WorkspaceApi.IdMappingType): List<String> {
        return when (mappingType) {
            WorkspaceApi.IdMappingType.WS_SYS_ID_TO_ID -> identifiers.map {
                workspaceService.getWorkspaceIdBySystemId(it)
            }
            WorkspaceApi.IdMappingType.WS_ID_TO_SYS_ID -> identifiers.map {
                workspaceService.getSystemId(it)
            }
            WorkspaceApi.IdMappingType.NO_MAPPING -> identifiers
        }
    }

    fun evictUserWorkspaces(userId: String) {
        userWorkspacesCache.asMap().keys.removeIf { it.user == userId }
    }

    fun evictUserWorkspacesForAuthority(authorityRef: EntityRef) {
        if (authorityRef.getSourceId() == AuthorityType.GROUP.sourceId) {
            userWorkspacesCache.invalidateAll()
        } else {
            evictUserWorkspaces(authorityRef.getLocalId())
        }
    }

    fun evictAllUserWorkspaces() {
        userWorkspacesCache.invalidateAll()
    }

    fun evictIsUserManagerOfForWorkspace(workspaceId: String) {
        isUserManagerOfCache.asMap().keys.removeIf { it.workspace == workspaceId }
    }

    fun evictIsUserManagerOfForUser(userId: String) {
        isUserManagerOfCache.asMap().keys.removeIf { it.user == userId }
    }

    fun evictAllIsUserManagerOf() {
        isUserManagerOfCache.invalidateAll()
    }

    fun evictWorkspaceManagersRefs(workspaceId: String) {
        workspaceManagersRefsCache.invalidate(workspaceId)
    }

    private data class UserWorkspacesCacheKey(
        val user: String,
        val membershipType: WsMembershipType
    )

    private data class IsUserManagerOfKey(
        val user: String,
        val workspace: String
    )
}
