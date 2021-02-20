package ru.citeck.ecos.model.type.domain

import ru.citeck.ecos.model.association.domain.AssociationEntity
import ru.citeck.ecos.model.domain.AbstractAuditingEntity
import ru.citeck.ecos.model.utils.EntityCollectionUtils
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "ecos_type")
class TypeEntity : AbstractAuditingEntity() {

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
    var sourceId: String? = null
    var createVariants: String? = null
    var inheritActions = false
    var defaultCreateVariant: Boolean? = null
    var postCreateActionRef: String? = null

    @ManyToOne(cascade = [CascadeType.DETACH])
    @JoinColumn(name = "parent_id")
    var parent: TypeEntity? = null

    @OneToMany(mappedBy = "source", fetch = FetchType.EAGER, cascade = [CascadeType.ALL], orphanRemoval = true)
    val associations: Set<AssociationEntity> = HashSet()

    @Column(name = "actions_str")
    var actions: String? = null

    var model: String? = null

    var docLib: String? = null

    fun setAssociations(associations: Set<AssociationEntity>) {
        EntityCollectionUtils.changeHibernateSet(this.associations, associations) { it.id }
    }
}
