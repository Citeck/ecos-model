package ru.citeck.ecos.model.type.repository

import ru.citeck.ecos.model.domain.AbstractAuditingEntity
import ru.citeck.ecos.records2.RecordConstants
import javax.persistence.*

@Entity
@Table(name = "ecos_type")
class TypeEntity : AbstractAuditingEntity() {
    companion object {

        fun replaceNameValid(attributeName: String): String {
            if (RecordConstants.ATT_MODIFIED.equals(attributeName)) {
                return "lastModifiedDate"
            }
            if (RecordConstants.ATT_MODIFIER.equals(attributeName)) {
                return "lastModifiedBy"
            }
            if ("moduleId".equals(attributeName)) {
                return "extId"
            }
            return attributeName
        }

        fun isAttributeNameNotValid(attributeName: String): Boolean {
            return RecordConstants.ATT_TYPE.equals(attributeName)
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_types_seq_gen")
    @SequenceGenerator(name = "ecos_types_seq_gen")
    val id: Long? = null

    @Column(unique = true)
    lateinit var extId: String

    @Column(nullable = false)
    var name: String? = null
    var description: String? = null
    var tenant: String? = null
    var form: String? = null
    var journal: String? = null
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

    // storageType
    var sourceType: String? = null
    var sourceId: String? = null
    var sourceRef: String? = null

    var createVariants: String? = null
    var inheritActions = false
    var defaultCreateVariant: Boolean? = null
    var postCreateActionRef: String? = null
    var associations: String? = null

    @ManyToOne(cascade = [CascadeType.DETACH])
    @JoinColumn(name = "parent_id")
    var parent: TypeEntity? = null

    @Column(name = "actions_str")
    var actions: String? = null

    var model: String? = null

    var docLib: String? = null

    var contentConfig: String? = null

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
