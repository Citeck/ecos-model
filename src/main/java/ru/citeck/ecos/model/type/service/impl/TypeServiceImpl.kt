package ru.citeck.ecos.model.type.service.impl

import lombok.Data
import org.apache.commons.lang3.StringUtils
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.json.Json.mapper
import ru.citeck.ecos.model.association.dto.AssociationDto
import ru.citeck.ecos.model.converter.DtoConverter
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.model.service.exception.ForgottenChildsException
import ru.citeck.ecos.model.type.domain.TypeEntity
import ru.citeck.ecos.model.type.dto.TypeDef
import ru.citeck.ecos.model.type.repository.TypeRepository
import ru.citeck.ecos.model.type.service.TypeService
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.PredicateUtils
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.ValuePredicate
import ru.citeck.ecos.records3.record.mixin.impl.mutmeta.MutMetaMixin
import java.time.Instant
import java.util.*
import java.util.function.Consumer
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Root

@Service
class TypeServiceImpl(
    private val typeRepository: TypeRepository,
    private val typeConverter: DtoConverter<TypeDef, TypeEntity>
) : TypeService {

    companion object {
        private val PROTECTED_TYPES: Set<String> = setOf(
            "base",
            "case",
            "document",
            "number-template",
            "type",
            "user-base",
            "file",
            "directory"
        )
    }

    private var onTypeChangedListener: (TypeDef) -> Unit = {}

    override fun getFullAssocs(typeId: String): List<AssociationDto> {
        TODO("Not yet implemented")
    }

    override fun getChildren(typeId: String): List<String> {
        return typeRepository.getChildrenIds(typeId).toList()
    }

    override fun getAll(max: Int, skip: Int, predicate: Predicate): List<TypeDef> {
        return getAll(max, skip, predicate, null)
    }

    override fun getAll(max: Int, skip: Int, predicate: Predicate, sort: Sort?): List<TypeDef> {

        if (max == 0) {
            return emptyList()
        }

        val querySort = sort ?: Sort.by(Sort.Direction.DESC, "id")
        val page = PageRequest.of(skip / max, max, querySort)

        return typeRepository.findAll(toSpec(predicate), page)
            .map { typeConverter.entityToDto(it) }
            .toList()
    }

    override fun getAll(max: Int, skip: Int): List<TypeDef> {
        val page = PageRequest.of(skip / max, max, Sort.by(Sort.Direction.DESC, "id"))
        return typeRepository.findAll(page)
            .map { typeConverter.entityToDto(it) }
            .toList()
    }

    override fun getCount(): Long {
        return typeRepository.count().toLong()
    }

    override fun getCount(predicate: Predicate): Long {
        val spec = toSpec(predicate)
        return if (spec != null) {
            typeRepository.count(spec)
        } else {
            getCount()
        }
    }

    override fun addListener(onTypeChangedListener: Consumer<TypeDef>) {
        this.onTypeChangedListener = { onTypeChangedListener.accept(it) }
    }

    override fun getAll(): List<TypeDef> {
        return typeRepository.findAll().map { typeConverter.entityToDto(it) }
    }

    override fun getParentIds(id: String): List<String> {

        var typeEntity = typeRepository.findByExtId(id)
        val parents = mutableListOf<String>()
        while (typeEntity != null) {
            parents.add(typeEntity.extId)
            typeEntity = typeEntity.parent
        }
        return parents
    }

    override fun expandTypes(typeIds: Collection<String>): List<TypeDef> {
        if (typeIds.isEmpty()) {
            return emptyList()
        }
        val result: MutableList<TypeDef> = ArrayList()
        val resultIdsSet: MutableSet<String> = HashSet()
        for (typeId in typeIds) {
            forEachTypeInDescHierarchy(typeId) { type ->
                if (resultIdsSet.add(type.extId)) {
                    result.add(typeConverter.entityToDto(type))
                }
            }
        }
        return result
    }

    private fun forEachTypeInDescHierarchy(id: String, action: (TypeEntity) -> Unit) {
        forEachTypeInDescHierarchy(typeRepository.findByExtId(id), action);
    }

    private fun forEachTypeInDescHierarchy(type: TypeEntity?, action: (TypeEntity) -> Unit) {
        if (type == null) {
            return
        }
        action.invoke(type)
        val childrenIds = getChildren(type.extId)
        for (childId in childrenIds) {
            forEachTypeInDescHierarchy(childId, action)
        }
    }

    private fun <T : Any> forEachTypeInAscHierarchy(typeId: String, action: (TypeEntity) -> T?): T? {
        var typeEntity = typeRepository.findByExtId(typeId)
        while (typeEntity != null) {
            val res = action.invoke(typeEntity)
            if (res != null) {
                return res
            }
            typeEntity = typeEntity.parent
        }
        return null
    }

    override fun getAll(typeIds: Collection<String>): List<TypeDef> {
        return typeRepository.findAllByExtIds(HashSet(typeIds)).map {
            typeConverter.entityToDto(it)
        }.toList()
    }

    override fun getById(typeId: String): TypeDef {
        return typeRepository.findByExtId(typeId)?.let { typeConverter.entityToDto(it) }
            ?: error("Type is not found: '$typeId'")
    }

    override fun getByIdOrNull(typeId: String): TypeDef? {
        return typeRepository.findByExtId(typeId)?.let { typeConverter.entityToDto(it) }
    }

    override fun getOrCreateByExtId(typeId: String): TypeDef {

        val byExtId: TypeEntity? = typeRepository.findByExtId(typeId)

        return byExtId?.let { typeConverter.entityToDto(it) } ?: {

            check(!("base" == typeId
                || "user-base" == typeId
                || "type" == typeId)
            ) {
                "Base type doesn't exists: '$typeId'"
            }

            val typeDef = TypeDef.create()
            typeDef.withId(typeId)
            typeDef.withParentRef(TypeUtils.getTypeRef("user-base"))
            typeDef.withName(MLText(typeId))

            save(typeDef.build())
        }()
    }

    private fun getBaseType(): TypeDef {
        return typeRepository.findByExtId("base")
            ?.let { typeConverter.entityToDto(it) }
            ?: error("Base type doesn't exists")
    }

    @Transactional
    override fun delete(typeId: String) {
        if (PROTECTED_TYPES.contains(typeId)) {
            throw RuntimeException("Type '$typeId' is protected")
        }
        typeRepository.findByExtId(typeId)?.let {
            if (it.children.size > 0) {
                throw ForgottenChildsException()
            }
            typeRepository.delete(it)
        }
    }

    @Transactional
    override fun save(dto: TypeDef): TypeDef {

        var entity = typeConverter.dtoToEntity(dto)
        entity = typeRepository.save(entity)

        updateModifiedTimeForLinkedTypes(dto.id)

        val typeDef = typeConverter.entityToDto(entity)
        onTypeChangedListener.invoke(typeDef)

        return typeDef
    }

    private fun updateModifiedTimeForLinkedTypes(typeId: String) {
        val action: (TypeEntity) -> Unit = { typeEntity ->
            if (typeEntity.extId != typeId) {
                typeEntity.lastModifiedDate = Instant.now()
                typeRepository.save(typeEntity)
            }
        }
        forEachTypeInDescHierarchy(typeId, action)
        forEachTypeInAscHierarchy(typeId, action)
    }

    // todo: this method should be in ecos-records-spring
    private fun toSpec(predicate: Predicate): Specification<TypeEntity>? {

        if (predicate is ValuePredicate) {

            val type = predicate.getType()
            val value: Any = predicate.getValue()
            val attribute = predicate.getAttribute()

            if (RecordConstants.ATT_MODIFIED == attribute && ValuePredicate.Type.GT == type) {
                val instant = mapper.convert(value, Instant::class.java)
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
                builder.like(builder.lower(root.get("name")), "%" + predicateDto.name!!.toLowerCase() + "%")
            }
        }
        if (StringUtils.isNotBlank(predicateDto.moduleId)) {
            val idSpec = Specification { root: Root<TypeEntity>,
                                         _: CriteriaQuery<*>?,
                                         builder: CriteriaBuilder ->
                builder.like(builder.lower(root.get("extId")), "%" + predicateDto.moduleId!!.toLowerCase() + "%")
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
