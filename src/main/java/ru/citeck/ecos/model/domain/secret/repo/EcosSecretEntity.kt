package ru.citeck.ecos.model.domain.secret.repo

import jakarta.persistence.*
import ru.citeck.ecos.model.domain.AbstractAuditingEntity

@Entity
@Table(name = "ecos_secret")
class EcosSecretEntity : AbstractAuditingEntity() {

    companion object {
        fun create(extId: String): EcosSecretEntity {
            val entity = EcosSecretEntity()
            entity.extId = extId
            return entity
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hibernate_sequence")
    @SequenceGenerator(name = "hibernate_sequence")
    val id: Long? = null

    @Column(unique = true)
    lateinit var extId: String

    @Column(nullable = false)
    var name: String? = null

    var type: String? = null

    var data: ByteArray? = null

    override fun equals(other: Any?): Boolean {
        if (other !is EcosSecretEntity) {
            return false
        }
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
