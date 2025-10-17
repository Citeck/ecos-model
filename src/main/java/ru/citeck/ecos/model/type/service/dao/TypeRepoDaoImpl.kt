package ru.citeck.ecos.model.type.service.dao

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import ru.citeck.ecos.model.lib.workspace.IdInWs
import ru.citeck.ecos.model.type.repository.TypeEntity
import ru.citeck.ecos.model.type.repository.TypeRepository
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverter
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverterFactory

@Component
class TypeRepoDaoImpl(
    private val repo: TypeRepository,
    private val predicateJpaService: JpaSearchConverterFactory
) : TypeRepoDao {

    private lateinit var searchConverter: JpaSearchConverter<TypeEntity>

    @PostConstruct
    fun init() {
        searchConverter = predicateJpaService.createConverter(TypeEntity::class.java)
            .withDefaultPageSize(10000)
            .build()
    }

    override fun save(entity: TypeEntity): TypeEntity {
        return repo.save(entity)
    }

    override fun delete(entity: TypeEntity) {
        return repo.delete(entity)
    }

    override fun findByExtId(typeId: IdInWs): TypeEntity? {
        return repo.findByExtIdInWs(typeId.workspace, typeId.id)
    }

    override fun findAllByTypeIds(typeIds: Collection<IdInWs>): Set<TypeEntity> {
        val result = LinkedHashSet<TypeEntity>()
        val extIdsByWorkspace = HashMap<String, MutableList<String>>()
        for (typeId in typeIds) {
            extIdsByWorkspace.computeIfAbsent(typeId.workspace) { ArrayList() }.add(typeId.id)
        }
        for ((ws, extIds) in extIdsByWorkspace) {
            repo.findAllByExtIdInWs(ws, extIds).forEach {
                result.add(it)
            }
        }
        return result
    }

    override fun getChildrenIds(parentId: IdInWs): Set<IdInWs> {
        return repo.getChildren(parentId.workspace, parentId.id).mapTo(LinkedHashSet()) {
            IdInWs.create(it.workspace, it.extId)
        }
    }

    override fun count(predicate: Predicate): Long {
        return searchConverter.getCount(repo, predicate)
    }

    override fun findAll(predicate: Predicate, max: Int, skip: Int, sort: List<SortBy>): List<TypeEntity> {
        return searchConverter.findAll(repo, predicate, max, skip, sort)
    }
}
