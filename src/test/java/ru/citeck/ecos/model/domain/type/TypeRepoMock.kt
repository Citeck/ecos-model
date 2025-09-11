package ru.citeck.ecos.model.domain.type

import ru.citeck.ecos.model.type.repository.TypeEntity
import ru.citeck.ecos.model.type.service.TypeId
import ru.citeck.ecos.model.type.service.dao.TypeRepoDao
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.RecordsServiceFactory
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField

class TypeRepoMock(recordsServiceFactory: RecordsServiceFactory) : TypeRepoDao {

    companion object {
        private val idCounter = AtomicLong()
    }

    private val data = ConcurrentHashMap<String, TypeEntity>()
    private val predicateService = recordsServiceFactory.predicateService

    override fun save(entity: TypeEntity): TypeEntity {
        if (!data.contains(entity.extId)) {
            val prop = TypeEntity::class.memberProperties.find { it.name == "id" }!!
            prop.isAccessible = true
            prop.javaField!!.set(entity, idCounter.incrementAndGet())
        }
        data[entity.extId] = entity
        return entity
    }

    override fun delete(entity: TypeEntity) {
        data.remove(entity.extId)
    }

    override fun findByExtId(typeId: TypeId): TypeEntity? {
        return data[typeId.id]
    }

    override fun findAllByTypeIds(typeIds: Collection<TypeId>): Set<TypeEntity> {
        return typeIds.mapNotNullTo(LinkedHashSet()) { findByExtId(it) }
    }

    override fun getChildrenIds(parentId: TypeId): Set<TypeId> {
        return data.values.filter { it.parent?.extId == parentId.id }
            .map { TypeId.create(it.extId) }
            .toSet()
    }

    override fun findAll(predicate: Predicate, max: Int, skip: Int, sort: List<SortBy>): List<TypeEntity> {
        return predicateService.filter(data.values, predicate)
    }

    override fun count(predicate: Predicate): Long {
        return findAll(predicate, 0, Int.MAX_VALUE, emptyList()).size.toLong()
    }
}
