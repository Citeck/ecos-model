package ru.citeck.ecos.model.type.service.dao

import ru.citeck.ecos.model.type.repository.TypeEntity
import ru.citeck.ecos.model.type.service.TypeId
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy

interface TypeRepoDao {

    fun save(entity: TypeEntity): TypeEntity

    fun delete(entity: TypeEntity)

    fun findByExtId(typeId: TypeId): TypeEntity?

    fun findAllByTypeIds(typeIds: Collection<TypeId>): Set<TypeEntity>

    fun getChildrenIds(parentId: TypeId): Set<TypeId>

    fun findAll(predicate: Predicate, max: Int, skip: Int, sort: List<SortBy>): List<TypeEntity>

    fun count(predicate: Predicate): Long
}
