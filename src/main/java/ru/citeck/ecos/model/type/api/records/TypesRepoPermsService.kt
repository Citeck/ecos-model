package ru.citeck.ecos.model.type.api.records

import org.springframework.stereotype.Service
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.model.lib.workspace.IdInWs
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.model.lib.workspace.convertToIdInWsSafe
import ru.citeck.ecos.model.type.service.TypesService
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.api.properties.EcosWebAppProps
import ru.citeck.ecos.webapp.lib.perms.EcosPermissionsService
import ru.citeck.ecos.webapp.lib.perms.RecordPerms
import ru.citeck.ecos.webapp.lib.perms.RecordPermsContext
import ru.citeck.ecos.webapp.lib.perms.component.RecordAttsPermsComponent
import ru.citeck.ecos.webapp.lib.perms.component.RecordAttsPermsData
import ru.citeck.ecos.webapp.lib.perms.component.RecordPermsComponent
import ru.citeck.ecos.webapp.lib.perms.component.RecordPermsData

@Service
class TypesRepoPermsService(
    val typesService: TypesService,
    val webAppProps: EcosWebAppProps,
    ecosPermissionsService: EcosPermissionsService,
    private val workspaceService: WorkspaceService
) {

    companion object {
        private const val PERMISSION_CREATE_CHILDREN = "create-children"
        private const val PERMISSION_DELETE = "delete"
        private const val PERMISSION_WRITE = "write"
        private const val PERMISSION_CREATE_OR_EDIT_TYPES = "create-or-edit-types"

        private const val ATT_CREATOR_USERNAME = RecordConstants.ATT_CREATOR + ScalarType.LOCAL_ID_SCHEMA
        private const val ATT_WORKSPACE_ID = RecordConstants.ATT_WORKSPACE + ScalarType.LOCAL_ID_SCHEMA
    }

    private val permsCalculator = ecosPermissionsService.createCalculator()
        .defaultComponents()
        .addComponent(object : RecordPermsComponent, RecordAttsPermsComponent {

            override fun getRecordPerms(context: RecordPermsContext): RecordPermsData {
                return evalPerms(context)
            }

            override fun getRecordAttsPerms(context: RecordPermsContext): RecordAttsPermsData {
                return evalPerms(context)
            }

            private fun evalPerms(context: RecordPermsContext): DefaultPerms {
                return context.computeIfAbsent(this::class) {
                    val authorities = context.getAuthorities()
                    var hasWritePerms = authorities.contains(AuthRole.ADMIN) ||
                        authorities.contains(AuthRole.SYSTEM)
                    if (!hasWritePerms) {
                        val recordAtts = context.getRecord().getAtts(listOf(ATT_CREATOR_USERNAME, ATT_WORKSPACE_ID))
                        if (recordAtts[ATT_CREATOR_USERNAME].asText() == context.getUser()) {
                            hasWritePerms = true
                        } else {
                            val workspaceId = recordAtts[ATT_WORKSPACE_ID].asText()
                            if (workspaceId.isNotBlank() &&
                                workspaceService.isUserManagerOf(context.getUser(), workspaceId)
                            ) {
                                hasWritePerms = true
                            }
                        }
                    }
                    DefaultPerms(hasWritePerms)
                }
            }
        })
        .build()

    fun getPermissions(record: Any): RecordPerms {
        return permsCalculator.getPermissions(record)
    }

    fun checkMutationPermissions(record: TypesRepoRecordsMutDao.TypeMutRecord) {

        if (AuthContext.isRunAsSystemOrAdmin()) {
            return
        }
        if (record.workspace.isNotEmpty()) {
            if (workspaceService.isUserManagerOf(AuthContext.getCurrentUser(), record.workspace)) {
                return
            } else {
                throwPermissionDenied(PERMISSION_CREATE_OR_EDIT_TYPES)
            }
        }

        val parentBefore = getRepoRef(record.baseTypeDef.parentRef)
        val parentAfter = getRepoRef(record.parentRef)

        if (record.isNewRec() || parentBefore != parentAfter) {
            val parentPerms = permsCalculator.getPermissions(parentAfter)
            if (!parentPerms.hasPermission(PERMISSION_CREATE_CHILDREN)) {
                throwPermissionDenied(PERMISSION_CREATE_CHILDREN)
            }
        }

        if (!record.isNewRec()) {
            val perms = permsCalculator.getPermissions(getRepoRef(IdInWs.create(record.workspace, record.id)))
            if (perms.hasWritePerms()) {
                return
            }
            val currentDef = typesService.getByIdWithMetaOrNull(IdInWs.create(record.workspace, record.id))
            val creator = currentDef?.meta?.creator ?: ""
            if (creator.isBlank() || creator != AuthContext.getCurrentUser()) {
                throwPermissionDenied(PERMISSION_WRITE)
            }
        }
    }

    fun checkDeletePermissions(typeId: String) {
        if (AuthContext.isRunAsSystemOrAdmin()) {
            return
        }

        val perms = permsCalculator.getPermissions(getRepoRef(typeId))
        if (perms.hasWritePerms()) {
            return
        }
        val ecosTypeId = workspaceService.convertToIdInWsSafe(typeId)
        val currentDef = typesService.getByIdWithMetaOrNull(ecosTypeId)
        if (currentDef == null) {
            throwPermissionDenied(PERMISSION_DELETE)
        } else if (currentDef.meta.creator == AuthContext.getCurrentUser()) {
            return
        }
        throwPermissionDenied(PERMISSION_DELETE)
    }

    fun throwPermissionDenied(permission: String) {
        error("Permission denied: $permission")
    }

    private fun getRepoRef(ref: Any): EntityRef {
        val typeId = when (ref) {
            is EntityRef -> ref.getLocalId().ifBlank { "base" }
            is String -> ref
            is IdInWs -> workspaceService.convertToStrId(ref)
            else -> error("invalid ref: $ref")
        }
        return EntityRef.create(webAppProps.appName, TypesRepoRecordsDao.ID, typeId)
    }

    private class DefaultPerms(
        private val hasWritePerms: Boolean
    ) : RecordPermsData,
        RecordAttsPermsData {
        override fun getAdditionalPerms(): Set<String> {
            return emptySet()
        }
        override fun hasAttReadPerms(name: String): Boolean {
            return true
        }
        override fun hasAttWritePerms(name: String): Boolean {
            return hasWritePerms
        }
        override fun hasReadPerms(): Boolean {
            return true
        }
        override fun hasWritePerms(): Boolean {
            return hasWritePerms
        }
    }
}
