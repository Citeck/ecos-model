package ru.citeck.ecos.model.domain.type

import org.springframework.data.domain.Sort
import ru.citeck.ecos.model.type.repository.TypeEntity
import ru.citeck.ecos.model.type.service.dao.TypeRepoDao
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.RecordsServiceFactory
import java.util.concurrent.ConcurrentHashMap

class TypeRepoMock(recordsServiceFactory: RecordsServiceFactory) : TypeRepoDao {

    private val data = ConcurrentHashMap<String, TypeEntity>()
    private val predicateService = recordsServiceFactory.predicateService

    override fun save(entity: TypeEntity): TypeEntity {
        data[entity.extId] = entity
        return entity
    }

    override fun delete(entity: TypeEntity) {
        data.remove(entity.extId)
    }

    override fun findByExtId(extId: String): TypeEntity? {
        return data[extId]
    }

    override fun findAllByExtIds(extIds: Set<String>): Set<TypeEntity> {
        return extIds.mapNotNull { findByExtId(it) }.toSet()
    }

    override fun getChildrenIds(parentId: String): Set<String> {
        return data.values.filter { it.parent?.extId == parentId }.map { it.extId }.toSet()
    }

    override fun findAll(predicate: Predicate, max: Int, skip: Int, sort: Sort?): List<TypeEntity> {
        return predicateService.filter(data.values, predicate)
    }

    override fun count(predicate: Predicate): Long {
        return findAll(predicate, 0, Int.MAX_VALUE, null).size.toLong()
    }
}
