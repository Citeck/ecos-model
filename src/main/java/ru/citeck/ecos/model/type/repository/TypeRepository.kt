package ru.citeck.ecos.model.type.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import ru.citeck.ecos.model.type.domain.TypeEntity

@Repository
interface TypeRepository : JpaRepository<TypeEntity, Long>, JpaSpecificationExecutor<TypeEntity> {

    @Query("SELECT TYPE FROM TypeEntity TYPE WHERE TYPE.extId = ?1")
    fun findByExtId(extId: String): TypeEntity?

    fun existsByExtId(extId: String): Boolean

    @Query("SELECT TYPE FROM TypeEntity TYPE WHERE TYPE.extId IN ?1")
    fun findAllByExtIds(extIds: Set<String>): Set<TypeEntity>

    fun findAllByParent(parent: TypeEntity): Set<TypeEntity>

    @Query("SELECT TYPE.extId FROM TypeEntity TYPE WHERE TYPE.parent = ?1")
    fun getChildrenIds(parentId: String): Set<String>

    @Query("SELECT TYPE " +
        "FROM TypeEntity TYPE " +
        "JOIN TYPE.aliases aliases " +
        "WHERE ?1 = aliases")
    fun findByContainsInAliases(alias: String): TypeEntity?
}
