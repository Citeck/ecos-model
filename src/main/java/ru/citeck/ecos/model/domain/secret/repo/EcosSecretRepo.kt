package ru.citeck.ecos.model.domain.secret.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository

@Repository
interface EcosSecretRepo : JpaRepository<EcosSecretEntity, Long>, JpaSpecificationExecutor<EcosSecretEntity> {

    fun findByExtId(extId: String): EcosSecretEntity?

    fun deleteByExtId(extId: String)
}
