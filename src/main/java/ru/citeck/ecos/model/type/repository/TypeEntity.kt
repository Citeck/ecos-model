package ru.citeck.ecos.model.type.repository

import jakarta.persistence.*
import ru.citeck.ecos.model.domain.AbstractAuditingEntity
import ru.citeck.ecos.model.lib.type.dto.QueryPermsPolicy
import ru.citeck.ecos.model.lib.type.dto.WorkspaceScope

@Entity
@Table(name = "ecos_type")
class TypeEntity : AbstractAuditingEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hibernate_sequence")
    @SequenceGenerator(name = "hibernate_sequence")
    val id: Long? = null

    @Column(unique = true)
    lateinit var extId: String

    var localIdTemplate: String? = null
    @Column(nullable = false)
    var name: String? = null
    var description: String? = null
    var tenant: String? = null
    var form: String? = null
    var journal: String? = null
    var defaultStatus: String? = null
    var board: String? = null
    var metaRecord: String? = null
    var attributes: String? = null
    var system: Boolean? = null
    var dashboardType: String? = null
    var configForm: String? = null
    var config: String? = null
    var dispNameTemplate: String? = null
    var numTemplateRef: String? = null
    var inheritNumTemplate: Boolean? = null
    var inheritForm: Boolean? = null
    var inheritCreateVariants: Boolean? = null

    // storageType
    var sourceType: String? = null
    var sourceId: String? = null
    var sourceRef: String? = null

    var createVariants: String? = null
    var inheritActions = false
    var defaultCreateVariant: Boolean? = null
    var createVariantsForChildTypes: Boolean? = null
    var postCreateActionRef: String? = null
    var associations: String? = null

    var workspace: String = ""
    @Enumerated(EnumType.STRING)
    var workspaceScope: WorkspaceScope? = null
    var defaultWorkspace: String? = null

    @ManyToOne(cascade = [CascadeType.DETACH])
    @JoinColumn(name = "parent_id")
    var parent: TypeEntity? = null

    @Column(name = "actions_str")
    var actions: String? = null

    var model: String? = null

    var docLib: String? = null

    var contentConfig: String? = null

    var aspects: String? = null

    @Enumerated(EnumType.STRING)
    var queryPermsPolicy: QueryPermsPolicy? = null

    var assignablePerms: String? = null

    override fun equals(other: Any?): Boolean {
        if (other !is TypeEntity) {
            return false
        }
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
