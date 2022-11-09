package ru.citeck.ecos.model.type.service.dao

import lombok.Data
import mu.KotlinLogging
import org.apache.commons.lang3.StringUtils
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.model.type.repository.TypeEntity
import ru.citeck.ecos.model.type.repository.TypeRepository
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.PredicateUtils
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
            val configRegex = getConfigRegexFromPredicate(predicate)
            return if (configRegex != null) {
                selectResult.filter { typeEntity ->
                    if (typeEntity.config != null) {
                        val configValue = typeEntity.config
                        configValue!!.matches(Regex(configRegex))
                    } else {
                        false
                    }
                }.toList()
            } else {
                selectResult.toList()
            }
        } else {
            return if (page == null) {
                repo.findAll().toList()
            } else {
                repo.findAll(page).toList()
            }
        }
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
            val systemSpec: Specification<TypeEntity> =
                if (!predicateDto.system) {
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
            if (configValue is String) {
                substructure += QUOTE_SYMBOL + configValue + QUOTE_SYMBOL
            } else {
                substructure += configValue
            }
            return Specification { root: Root<TypeEntity>,
                                   _: CriteriaQuery<*>?,
                                   builder: CriteriaBuilder ->
                builder.like(root.get(CONFIG_PROP), "%$substructure%")
            }
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

    private fun getField(attributeName: String):Field?{
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

    private fun getConfigRegexFromPredicate(predicate: Predicate?): String? {
        if (predicate == null) {
            return null
        }
        if (predicate is ComposedPredicate) {
            val conditions: ArrayList<String> = ArrayList<String>()
            predicate.getPredicates().forEach(
                Consumer { subPredicate: Predicate? ->
                    var subCondition: String? = null
                    if (subPredicate is ValuePredicate) {
                        subCondition = getCondition(subPredicate)
                    } else if (subPredicate is ComposedPredicate) {
                        subCondition = getConfigRegexFromPredicate(subPredicate)
                    }
                    if (subCondition != null) {
                        conditions.add(subCondition)
                    }
                })
            if (conditions.isNotEmpty()) {
                var result: String? = ""
                if (conditions.size > 1) {
                    for (idx in 0 until conditions.size) {
                        result +=
                            if (predicate is AndPredicate) {
                                "(?=" + conditions[idx] + ")"
                            } else {
                                "|" + conditions[idx]
                            }
                    }
                    if (predicate is OrPredicate) {
                        result = "($result)"
                    } else {
                        result += ".*"
                    }
                } else {
                    result = conditions[0]
                }
                return result
            }
        } else if (predicate is ValuePredicate) {
            return getCondition(predicate)
        }
        log.warn("Unexpected predicate class: {}", predicate.javaClass)
        return null
    }

    private fun getCondition(valuePredicate: ValuePredicate): String? {
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
                    substructure += QUOTE_SYMBOL + innerProps[idx] + QUOTE_SYMBOL + ".*:.*"
                }
                substructure += QUOTE_SYMBOL + innerProps[innerProps.size - 1] + QUOTE_SYMBOL + ":"
            }

            if (configValue is String) {
                substructure += QUOTE_SYMBOL + configValue + QUOTE_SYMBOL
            } else {
                substructure += configValue
            }
            if (valuePredicate.getType() == ValuePredicate.Type.EQ) {
                substructure += " *(,|})"
            }
            return ".*$substructure.*"
        }
        return null
    }

    @Data
    class PredicateDto {
        val name: String? = null
        val moduleId: String? = null
        val system: Boolean? = null
        val config: String? = null
    }
}
