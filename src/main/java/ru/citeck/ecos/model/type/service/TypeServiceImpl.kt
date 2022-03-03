package ru.citeck.ecos.model.type.service

import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.model.type.converter.TypeConverter
import ru.citeck.ecos.model.type.repository.TypeEntity
import ru.citeck.ecos.model.type.dto.TypeDef
import ru.citeck.ecos.model.type.service.dao.TypeRepoDao
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.VoidPredicate
import java.time.Instant
import java.util.*
import java.util.function.Consumer

@Service
class TypeServiceImpl(
    private val typeConverter: TypeConverter,
    private val typeRepoDao: TypeRepoDao,
    private val typeEventsService: TypeEventsService? = null
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

    override fun getChildren(typeId: String): List<String> {
        return typeRepoDao.getChildrenIds(typeId).toList()
    }

    override fun getAll(max: Int, skip: Int, predicate: Predicate): List<TypeDef> {
        return getAll(max, skip, predicate, null)
    }

    override fun getAll(max: Int, skip: Int, predicate: Predicate, sort: Sort?): List<TypeDef> {
        if (max <= 0) {
            return emptyList()
        }
        return typeRepoDao.findAll(predicate, max, skip, sort)
            .map { typeConverter.toDto(it) }
            .toList()
    }

    override fun getAll(max: Int, skip: Int): List<TypeDef> {
        if (max <= 0) {
            return emptyList()
        }
        return typeRepoDao.findAll(VoidPredicate.INSTANCE, max, skip, null)
            .map { typeConverter.toDto(it) }
            .toList()
    }

    override fun getCount(): Long {
        return typeRepoDao.count(VoidPredicate.INSTANCE)
    }

    override fun getCount(predicate: Predicate): Long {
        return typeRepoDao.count(predicate)
    }

    override fun addListener(onTypeChangedListener: Consumer<TypeDef>) {
        this.onTypeChangedListener = { onTypeChangedListener.accept(it) }
    }

    override fun getAll(): List<TypeDef> {
        return typeRepoDao.findAll(VoidPredicate.INSTANCE, 0, 10000, null)
            .map { typeConverter.toDto(it) }
    }

    override fun getParentIds(id: String): List<String> {

        var typeEntity = typeRepoDao.findByExtId(id)
        val parents = mutableListOf<String>()
        while (typeEntity != null) {
            parents.add(typeEntity.extId)
            typeEntity = typeEntity.parent
        }
        if (parents.isEmpty() || parents.last() != "base") {
            parents.add("base")
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
                    result.add(typeConverter.toDto(type))
                }
            }
        }
        return result
    }

    private fun forEachTypeInDescHierarchy(id: String, action: (TypeEntity) -> Unit) {
        forEachTypeInDescHierarchy(typeRepoDao.findByExtId(id), action);
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
        var typeEntity = typeRepoDao.findByExtId(typeId)
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
        return typeRepoDao.findAllByExtIds(HashSet(typeIds)).map {
            typeConverter.toDto(it)
        }.toList()
    }

    override fun getById(typeId: String): TypeDef {
        return typeRepoDao.findByExtId(typeId)?.let { typeConverter.toDto(it) }
            ?: error("Type is not found: '$typeId'")
    }

    override fun getByIdOrNull(typeId: String): TypeDef? {
        return typeRepoDao.findByExtId(typeId)?.let { typeConverter.toDto(it) }
    }

    override fun getOrCreateByExtId(typeId: String): TypeDef {

        val byExtId: TypeEntity? = typeRepoDao.findByExtId(typeId)
        if (byExtId != null) {
            return typeConverter.toDto(byExtId)
        }

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

        return save(typeDef.build())
    }

    private fun getBaseType(): TypeDef {
        return typeRepoDao.findByExtId("base")
            ?.let { typeConverter.toDto(it) }
            ?: error("Base type doesn't exists")
    }

    @Transactional
    override fun delete(typeId: String) {
        if (PROTECTED_TYPES.contains(typeId)) {
            throw RuntimeException("Type '$typeId' is protected")
        }
        val typeEntity = typeRepoDao.findByExtId(typeId) ?: return
        if (typeRepoDao.getChildrenIds(typeId).isNotEmpty()) {
            error("Type $typeId contains children and can't be deleted")
        }
        typeRepoDao.delete(typeEntity)
    }

    override fun deleteWithChildren(typeId: String) {

        if (PROTECTED_TYPES.contains(typeId)) {
            throw RuntimeException("Type '$typeId' is protected")
        }

        val typeEntity = typeRepoDao.findByExtId(typeId) ?: return
        val children = typeRepoDao.getChildrenIds(typeId)

        children.forEach { childId ->
            typeRepoDao.findByExtId(childId)?.let {
                typeRepoDao.delete(it)
            }
        }

        typeRepoDao.delete(typeEntity)
    }

    @Transactional
    override fun save(dto: TypeDef): TypeDef {

        var typeDefBefore: TypeDef? = null
        if (typeEventsService != null) {
            typeDefBefore = typeRepoDao.findByExtId(dto.id)?.let { typeConverter.toDto(it) }
        }

        var entity = typeConverter.toEntity(dto)

        entity = typeRepoDao.save(entity)

        updateModifiedTimeForLinkedTypes(dto.id)

        val typeDef = typeConverter.toDto(entity)
        onTypeChangedListener.invoke(typeDef)

        if (typeDefBefore == null) {
            typeEventsService?.onTypeCreated(typeDef)
        } else {
            typeEventsService?.onTypeChanged(typeDefBefore, typeDef)
        }

        return typeDef
    }

    private fun updateModifiedTimeForLinkedTypes(typeId: String) {
        val action: (TypeEntity) -> Unit = { typeEntity ->
            if (typeEntity.extId != typeId) {
                typeEntity.lastModifiedDate = Instant.now()
                typeRepoDao.save(typeEntity)
            }
        }
        forEachTypeInDescHierarchy(typeId, action)
        forEachTypeInAscHierarchy(typeId, action)
    }
}
