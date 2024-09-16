package ru.citeck.ecos.model.domain.workspace.dto

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.json.serialization.annotation.IncludeNonDefault
import ru.citeck.ecos.webapp.api.entity.EntityRef

enum class WorkspaceVisibility {
    PUBLIC,
    PRIVATE
}

enum class WorkspaceMemberRole {
    USER,
    MANAGER
}

enum class WorkspaceAction {
    JOIN
}

@IncludeNonDefault
@JsonDeserialize(builder = WorkspaceMember.Builder::class)
data class WorkspaceMember(
    val id: String,
    val authority: EntityRef,
    val memberRole: WorkspaceMemberRole
) {

    companion object {
        @JvmField
        val EMPTY = create {}

        fun create(): Builder {
            return Builder()
        }

        fun create(builder: Builder.() -> Unit): WorkspaceMember {
            val builderObj = Builder()
            builder.invoke(builderObj)
            return builderObj.build()
        }
    }

    class Builder() {

        var id: String = ""
        var authority: EntityRef = EntityRef.EMPTY
        var memberRole: WorkspaceMemberRole = WorkspaceMemberRole.USER

        constructor(base: WorkspaceMember) : this() {
            this.id = base.id
            this.authority = base.authority
            this.memberRole = base.memberRole
        }

        fun withId(id: String?): Builder {
            this.id = id ?: ""
            return this
        }

        fun withAuthority(authority: EntityRef?): Builder {
            this.authority = authority ?: EntityRef.EMPTY
            return this
        }

        fun withMemberRole(memberRole: WorkspaceMemberRole?): Builder {
            this.memberRole = memberRole ?: WorkspaceMemberRole.USER
            return this
        }

        fun build(): WorkspaceMember {
            return WorkspaceMember(id, authority, memberRole)
        }
    }
}

@IncludeNonDefault
@JsonDeserialize(builder = Workspace.Builder::class)
data class Workspace(
    val id: String,
    val name: MLText,
    val description: MLText = MLText.EMPTY,
    val workspaceMembers: List<WorkspaceMember> = emptyList(),
    val visibility: WorkspaceVisibility
) {

    companion object {
        @JvmField
        val EMPTY = create {}

        fun create(): Builder {
            return Builder()
        }

        fun create(builder: Builder.() -> Unit): Workspace {
            val builderObj = Builder()
            builder.invoke(builderObj)
            return builderObj.build()
        }
    }

    class Builder() {

        var id: String = ""
        var name: MLText = MLText.EMPTY
        var description: MLText = MLText.EMPTY
        var workspaceMembers: List<WorkspaceMember> = emptyList()
        var visibility: WorkspaceVisibility = WorkspaceVisibility.PUBLIC

        constructor(base: Workspace) : this() {
            this.id = base.id
            this.name = base.name
            this.description = base.description
            this.workspaceMembers = base.workspaceMembers
            this.visibility = base.visibility
        }

        fun withId(id: String?): Builder {
            this.id = id ?: ""
            return this
        }

        fun withName(name: MLText?): Builder {
            this.name = name ?: MLText.EMPTY
            return this
        }

        fun withDescription(description: MLText?): Builder {
            this.description = description ?: MLText.EMPTY
            return this
        }

        fun withWorkspaceMembers(workspaceMembers: List<WorkspaceMember>?): Builder {
            this.workspaceMembers = workspaceMembers ?: emptyList()
            return this
        }

        fun withVisibility(visibility: WorkspaceVisibility?): Builder {
            this.visibility = visibility ?: WorkspaceVisibility.PRIVATE
            return this
        }

        fun build(): Workspace {
            return Workspace(id, name, description, workspaceMembers, visibility)
        }
    }
}
