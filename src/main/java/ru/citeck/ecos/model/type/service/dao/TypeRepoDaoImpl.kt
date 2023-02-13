package ru.citeck.ecos.model.type.service.dao

import mu.KotlinLogging
import org.apache.commons.lang3.StringUtils
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.model.type.repository.TypeEntity
import ru.citeck.ecos.model.type.repository.TypeRepository
import ru.citeck.ecos.records2.predicate.model.*
import java.lang.reflect.Field
import java.time.DateTimeException
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.function.Consumer
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Root

@Component
class TypeRepoDaoImpl(private val repo: TypeRepository) : TypeRepoDao {

    companion object {
        private val log = KotlinLogging.logger {}

        private const val CONFIG_PROP = "config"
        private const val POINT_SYMBOL = "."
        private const val QUOTE_SYMBOL = "\""
        private const val LIKE_SYMBOL = "%"
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

    override fun findAll(predicate: Predicate, max: Int, skip: Int, sort: Sort?): List<TypeEntity> {
        if (max <= 0) {
            return emptyList()
        }
        val querySort = sort ?: Sort.by(Sort.Direction.DESC, "id")
        val page = PageRequest.of(skip / max, max, querySort)

        return getEntityList(predicate, page)
    }

    override fun count(predicate: Predicate): Long {
        return getEntityList(predicate, null).count().toLong()
    }

    private fun getEntityList(predicate: Predicate, page: PageRequest?): List<TypeEntity> {
        val specification = specificationFromPredicate(predicate)
        if (specification != null) {
            var selectResult =
                if (page != null) {
                    repo.findAll(specification, page)
                } else {
                    repo.findAll(specification)
                }
               return selectResult.toList()
        } else {
            return if (page == null) {
                repo.findAll().toList()
            } else {
                repo.findAll(page).toList()
            }
        }
    }

    private fun specificationFromPredicate(predicate: Predicate?): Specification<TypeEntity>? {
        if (predicate == null) {
            return null
        }
        var result: Specification<TypeEntity>? = null
        if (predicate is ComposedPredicate) {
            val specifications: ArrayList<Specification<TypeEntity>> = ArrayList<Specification<TypeEntity>>()
            predicate.getPredicates().forEach(
                Consumer { subPredicate: Predicate? ->
                    var subSpecification: Specification<TypeEntity>? = null
                    if (subPredicate is ValuePredicate) {
                        subSpecification = fromValuePredicate(subPredicate)
                    } else if (subPredicate is ComposedPredicate) {
                        subSpecification = specificationFromPredicate(subPredicate)
                    }
                    if (subSpecification != null) {
                        specifications.add(subSpecification)
                    }
                })
            if (specifications.isNotEmpty()) {
                result = specifications[0]
                if (specifications.size > 1) {
                    for (idx in 1 until specifications.size) {
                        result =
                            if (predicate is AndPredicate) {
                                result!!.and(specifications[idx])
                            } else {
                                result!!.or(specifications[idx])
                            }
                    }
                }
            }
            return result
        } else if (predicate is ValuePredicate) {
            return fromValuePredicate(predicate)
        }
        log.warn("Unexpected predicate class: {}", predicate.javaClass)
        return null
    }

    private fun fromValuePredicate(valuePredicate: ValuePredicate): Specification<TypeEntity>? {
        //ValuePredicate.Type.IN was not implemented
        if (StringUtils.isBlank(valuePredicate.getAttribute())) {
            return null
        }
        val attributeName = TypeEntity.replaceNameValid(StringUtils.trim(valuePredicate.getAttribute()))
        if (TypeEntity.isAttributeNameNotValid(attributeName)) {
            return null
        }
        if (attributeName.startsWith(CONFIG_PROP)) {
            var substructure = ""
            var configValue: Any = valuePredicate.getValue().asText()
            if (attributeName.contains(POINT_SYMBOL)) {
                configValue = getConfigValue(valuePredicate.getValue().asText())
                val innerProps = attributeName.split(POINT_SYMBOL)
                for (idx in 1 until innerProps.size - 1) {
                    substructure += QUOTE_SYMBOL + innerProps[idx] + QUOTE_SYMBOL + "%:%"
                }
                substructure += QUOTE_SYMBOL + innerProps[innerProps.size - 1] + QUOTE_SYMBOL + ":"
            }

            var unquotedSubstructure: String? = null
            var quotedSubstructure = LIKE_SYMBOL + substructure + QUOTE_SYMBOL + LIKE_SYMBOL +
                configValue + LIKE_SYMBOL + QUOTE_SYMBOL + LIKE_SYMBOL
            if (!(configValue is String)) {
                unquotedSubstructure = LIKE_SYMBOL + substructure + LIKE_SYMBOL + configValue + LIKE_SYMBOL
            }
            var configSpecification = Specification { root: Root<TypeEntity>,
                                                      _: CriteriaQuery<*>?,
                                                      builder: CriteriaBuilder ->
                builder.like(root.get(CONFIG_PROP), quotedSubstructure)
            }
            if (unquotedSubstructure != null) {
                configSpecification = configSpecification.or(
                    Specification { root: Root<TypeEntity>,
                                    _: CriteriaQuery<*>?,
                                    builder: CriteriaBuilder ->
                        builder.like(root.get(CONFIG_PROP), unquotedSubstructure)
                    })
            }
            return configSpecification
        }

        var specification: Specification<TypeEntity>? = null
        if ((ValuePredicate.Type.CONTAINS == valuePredicate.getType()
                || ValuePredicate.Type.LIKE == valuePredicate.getType())
            && isPropertyCanBeString(attributeName)
        ) {
            val attributeValue = "%" + valuePredicate.getValue().asText().lowercase() + "%"
            specification = Specification { root: Root<TypeEntity>,
                                            _: CriteriaQuery<*>?,
                                            builder: CriteriaBuilder ->
                builder.like(builder.lower(root.get(attributeName)), attributeValue)
            }

        } else {
            val objectValue: Comparable<*>? = getObjectValue(attributeName, valuePredicate.getValue().asText())
            if (objectValue != null) {
                if (ValuePredicate.Type.EQ == valuePredicate.getType()) {
                    specification = Specification { root: Root<TypeEntity>,
                                                    _: CriteriaQuery<*>?,
                                                    builder: CriteriaBuilder ->
                        builder.equal(root.get<Any>(attributeName), objectValue)
                    }
                } else if (ValuePredicate.Type.GT == valuePredicate.getType()) {
                    specification = Specification { root: Root<TypeEntity>,
                                                    _: CriteriaQuery<*>?,
                                                    builder: CriteriaBuilder ->
                        builder.greaterThan<Comparable<*>>(
                            root.get<Comparable<Comparable<*>>>(attributeName),
                            objectValue
                        )
                    }
                } else if (ValuePredicate.Type.GE == valuePredicate.getType()) {
                    specification = Specification { root: Root<TypeEntity>,
                                                    _: CriteriaQuery<*>?,
                                                    builder: CriteriaBuilder ->
                        builder.greaterThanOrEqualTo<Comparable<*>>(
                            root.get<Comparable<Comparable<*>>>(attributeName),
                            objectValue
                        )
                    }
                } else if (ValuePredicate.Type.LT == valuePredicate.getType()) {
                    specification = Specification { root: Root<TypeEntity>,
                                                    _: CriteriaQuery<*>?,
                                                    builder: CriteriaBuilder ->
                        builder.lessThan<Comparable<*>>(
                            root.get<Comparable<Comparable<*>>>(attributeName),
                            objectValue
                        )
                    }
                } else if (ValuePredicate.Type.LE == valuePredicate.getType()) {
                    specification = Specification { root: Root<TypeEntity>,
                                                    _: CriteriaQuery<*>?,
                                                    builder: CriteriaBuilder ->
                        builder.lessThanOrEqualTo<Comparable<*>>(
                            root.get<Comparable<Comparable<*>>>(attributeName),
                            objectValue
                        )
                    }
                }
            }
        }
        return specification
    }

    private fun isPropertyCanBeString(attributeName: String): Boolean {
        var result = true
        var searchField = getField(attributeName)
        if (searchField != null
            && (searchField.type == java.util.Date::class.java || searchField.type == Instant::class.java)) {
            result = false
        }
        return result
    }

    private fun getField(attributeName: String): Field? {
        var field: Field? = null
        try {
            field = TypeEntity::class.java.getDeclaredField(attributeName)
        } catch (e: NoSuchFieldException) {
            var superclass: Class<in Nothing> = TypeEntity::class.java.superclass
            while (field == null) {
                field = superclass.getDeclaredField(attributeName)
                superclass = superclass::class.java.superclass
            }
        }
        return field
    }

    private fun getObjectValue(attributeName: String, attributeValue: String): Comparable<*>? {
        var searchField = getField(attributeName)
        if (searchField != null)
            try {
                when (searchField.type) {
                    java.lang.String::class.java, String::class.java -> {
                        return attributeValue
                    }

                    java.lang.Long::class.java, Long::class.java -> {
                        return attributeValue.toLong()
                    }

                    java.lang.Boolean::class.java, Boolean::class.java -> {
                        return attributeValue.toBoolean()
                    }

                    java.util.Date::class.java -> {
                        //saved values has no milliseconds part cause of dateFormat
                        try {
                            val calendar = Calendar.getInstance()
                            calendar.timeInMillis = attributeValue.toLong()
                            calendar[Calendar.MILLISECOND] = 0
                            return calendar.time
                        } catch (formatException: NumberFormatException) {
                            try {
                                var valueObject =
                                    Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(attributeValue))
                                valueObject = valueObject.truncatedTo(ChronoUnit.SECONDS)
                                return Date.from(valueObject)
                            } catch (e: DateTimeException) {
                                log.error(
                                    "Failed to convert attribute '{}' value ({}) to date", attributeName,
                                    attributeValue, e
                                )
                            }
                        }
                    }

                    Instant::class.java -> {

                        return Json.mapper.convert(attributeValue.toLong(), Instant::class.java)
                    }

                    java.lang.Float::class.java, Float::class.java -> {
                        return attributeValue.toFloat()
                    }

                    else -> {
                        log.error("Unexpected attribute type {} for predicate", searchField.type)
                    }
                }
            } catch (e: NumberFormatException) {
                log.error(
                    "Failed to convert attribute '{}' value ({}) to number", attributeName,
                    attributeValue, e
                )
            }
        return null
    }

    private fun getConfigValue(attributeValue: String): Any {
        var result: Any? = attributeValue.toBooleanStrictOrNull()
        if (result == null) {
            try {
                result = attributeValue.toLong()
            } catch (e: NumberFormatException) {
                return attributeValue
            }
        }
        return result
    }
}
