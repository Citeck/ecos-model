package ru.citeck.ecos.model.type.service

import com.fasterxml.jackson.annotation.JsonIgnore
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.model.type.repository.TypeEntity
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef

data class TypeId private constructor(
    val workspace: String,
    val id: String
) {
    companion object {

        const val WS_DELIM = ModelUtils.WS_SCOPED_ARTIFACT_ID_DELIM
        val EMPTY = create("", "")

        @JvmStatic
        fun create(id: String): TypeId {
            return create("", id)
        }

        @JvmStatic
        fun create(workspace: String, id: String): TypeId {
            return TypeId(workspace.trim(), id.trim())
        }

        @JvmStatic
        fun WorkspaceService?.convertToTypeId(id: String): TypeId {
            return if (id.contains(WS_DELIM) && this != null) {
                val wsSysId = id.substringBefore(WS_DELIM)
                val idInWs = id.substringAfter(WS_DELIM)
                val workspaceId = this.getWorkspaceIdBySystemId(wsSysId)
                if (workspaceId.isBlank()) {
                    create("", id)
                } else {
                    create(workspaceId, idInWs)
                }
            } else {
                create("", id)
            }
        }

        @JvmStatic
        fun WorkspaceService?.convertToStrId(typeId: TypeId): String {
            if (typeId.workspace.isEmpty() || typeId.id.isEmpty()) {
                return typeId.id
            }
            if (this == null) {
                error("WorkspaceService is null")
            }
            return this.addWsPrefixToId(typeId.id, typeId.workspace)
        }

        fun TypeDef.getTypeId(): TypeId {
            return create(this.workspace, this.id)
        }

        fun TypeEntity.getTypeId(): TypeId {
            return create(this.workspace, this.extId)
        }
    }

    @JsonIgnore
    fun isEmpty() = id.isEmpty()

    override fun toString(): String {
        if (workspace.isEmpty()) {
            return id
        }
        return "$workspace$WS_DELIM$WS_DELIM$id"
    }
}
