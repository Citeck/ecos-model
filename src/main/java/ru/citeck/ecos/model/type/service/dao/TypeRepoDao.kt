package ru.citeck.ecos.model.type.service.dao

import org.springframework.data.domain.Sort
import ru.citeck.ecos.model.type.repository.TypeEntity
import ru.citeck.ecos.records2.predicate.model.Predicate

interface TypeRepoDao {

    fun save(entity: TypeEntity): TypeEntity

    fun delete(entity: TypeEntity)

    fun findByExtId(extId: String): TypeEntity?

    fun findAllByExtIds(extIds: Set<String>): Set<TypeEntity>

    fun getChildrenIds(parentId: String): Set<String>

    fun findAll(predicate: Predicate, max: Int, skip: Int, sort: Sort?): List<TypeEntity>

    fun count(predicate: Predicate): Long
}
