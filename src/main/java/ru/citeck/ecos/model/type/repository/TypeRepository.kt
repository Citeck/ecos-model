package ru.citeck.ecos.model.type.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface TypeRepository : JpaRepository<TypeEntity, Long>, JpaSpecificationExecutor<TypeEntity> {

    @Query("SELECT TYPE FROM TypeEntity TYPE WHERE TYPE.extId = ?1")
    fun findByExtId(extId: String): TypeEntity?

    @Query("SELECT TYPE FROM TypeEntity TYPE WHERE TYPE.workspace = ?1 AND TYPE.extId = ?2")
    fun findByExtIdInWs(workspace: String, extId: String): TypeEntity?

    @Query("SELECT TYPE FROM TypeEntity TYPE WHERE TYPE.workspace = ?1 AND TYPE.extId IN ?2")
    fun findAllByExtIdInWs(workspace: String, extId: List<String>): List<TypeEntity>

    @Query("SELECT TYPE FROM TypeEntity TYPE WHERE TYPE.workspace = ?1 AND TYPE.parent.workspace = ?1 AND TYPE.parent.extId = ?2")
    fun getChildren(parentWorkspace: String, parentId: String): List<TypeEntity>
}
