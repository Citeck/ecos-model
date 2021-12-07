package ru.citeck.ecos.model.type.service.dao

import lombok.Data
import org.apache.commons.lang3.StringUtils
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.model.type.repository.TypeEntity
import ru.citeck.ecos.model.type.repository.TypeRepository
import ru.citeck.ecos.model.type.service.TypeServiceImpl
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.PredicateUtils
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.ValuePredicate
import java.time.Instant
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Root

@Component
class TypeRepoDaoImpl(private val repo: TypeRepository) : TypeRepoDao {

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

    override fun findAll(predicate: Predicate, max: Int, skip: Int, sort: Sort?): List<TypeEntity> {
        if (max <= 0) {
            return emptyList()
        }
        val querySort = sort ?: Sort.by(Sort.Direction.DESC, "id")
        val page = PageRequest.of(skip / max, max, querySort)

        val spec = toSpec(predicate)
        return if (spec != null) {
            repo.findAll(spec, page)
        } else {
            repo.findAll(page)
        }.toList()
    }

    override fun count(predicate: Predicate): Long {
        return repo.count(toSpec(predicate))
    }

    private fun toSpec(predicate: Predicate): Specification<TypeEntity>? {

        if (predicate is ValuePredicate) {

            val type = predicate.getType()
            val value: Any = predicate.getValue()
            val attribute = predicate.getAttribute()

            if (RecordConstants.ATT_MODIFIED == attribute && ValuePredicate.Type.GT == type) {
                val instant = Json.mapper.convert(value, Instant::class.java)
                if (instant != null) {
                    return Specification { root: Root<TypeEntity>,
                                           _: CriteriaQuery<*>?,
                                           builder: CriteriaBuilder ->
                        builder.greaterThan(root.get<Any>("lastModifiedDate").`as`(Instant::class.java), instant)
                    }
                }
            }
        }
        val predicateDto = PredicateUtils.convertToDto(predicate, PredicateDto::class.java)

        var spec: Specification<TypeEntity>? = null
        if (StringUtils.isNotBlank(predicateDto.name)) {
            spec = Specification { root: Root<TypeEntity>,
                                   _: CriteriaQuery<*>?,
                                   builder: CriteriaBuilder ->
                builder.like(builder.lower(root.get("name")), "%" + predicateDto.name!!.lowercase() + "%")
            }
        }
        if (StringUtils.isNotBlank(predicateDto.moduleId)) {
            val idSpec = Specification { root: Root<TypeEntity>,
                                         _: CriteriaQuery<*>?,
                                         builder: CriteriaBuilder ->
                builder.like(builder.lower(root.get("extId")), "%" + predicateDto.moduleId!!.lowercase() + "%")
            }
            spec = spec?.or(idSpec) ?: idSpec
        }
        if (predicateDto.system != null) {
            val systemSpec: Specification<TypeEntity>
            systemSpec = if (!predicateDto.system) {
                Specification { root: Root<TypeEntity>,
                                _: CriteriaQuery<*>?,
                                builder: CriteriaBuilder ->
                    builder.not(builder.equal(root.get<Any>("system"), true))
                }
            } else {
                Specification { root: Root<TypeEntity>,
                                _: CriteriaQuery<*>?,
                                builder: CriteriaBuilder ->
                    builder.equal(root.get<Any>("system"), true)
                }
            }
            spec = spec?.and(systemSpec) ?: systemSpec
        }
        return spec
    }

    @Data
    class PredicateDto {
        val name: String? = null
        val moduleId: String? = null
        val system: Boolean? = null
    }
}
