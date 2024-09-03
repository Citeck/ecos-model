package ru.citeck.ecos.model.type.service.dao

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
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

    override fun findByExtId(extId: String): TypeEntity? {
        return repo.findByExtId(extId)
    }

    override fun findAllByExtIds(extIds: Set<String>): Set<TypeEntity> {
        return repo.findAllByExtIds(extIds)
    }

    override fun getChildrenIds(parentId: String): Set<String> {
        return repo.getChildrenIds(parentId)
    }

    override fun count(predicate: Predicate): Long {
        return searchConverter.getCount(repo, predicate)
    }

    override fun findAll(predicate: Predicate, max: Int, skip: Int, sort: List<SortBy>): List<TypeEntity> {
        return searchConverter.findAll(repo, predicate, max, skip, sort)
    }
}
