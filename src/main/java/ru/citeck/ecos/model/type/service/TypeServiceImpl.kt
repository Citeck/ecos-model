package ru.citeck.ecos.model.type.service

import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.entity.EntityWithMeta
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.model.type.converter.TypeConverter
import ru.citeck.ecos.model.type.repository.TypeEntity
import ru.citeck.ecos.model.type.service.dao.TypeRepoDao
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.VoidPredicate
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import java.time.Instant
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.BiConsumer
import java.util.function.Consumer
import kotlin.collections.HashSet

@Service
class TypeServiceImpl(
    private val typeConverter: TypeConverter,
    private val typeRepoDao: TypeRepoDao
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

    private var onTypeChangedListeners: MutableList<(
            EntityWithMeta<TypeDef>?,
            EntityWithMeta<TypeDef>?
        ) -> Unit> = CopyOnWriteArrayList()

    private var onDeletedListeners: MutableList<(String) -> Unit> = CopyOnWriteArrayList()

    private var onTypeHierarchyChangedListeners: MutableList<(Set<String>) -> Unit> = CopyOnWriteArrayList()

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

    override fun addListenerTypeHierarchyChangedListener(onTypeChangedListener: Consumer<Set<String>>) {
        onTypeHierarchyChangedListeners.add { onTypeChangedListener.accept(it) }
    }

    override fun addListener(onTypeChangedListener: BiConsumer<TypeDef?, TypeDef?>) {
        onTypeChangedListeners.add { before, after ->
            onTypeChangedListener.accept(before?.entity, after?.entity)
        }
    }

    override fun addListenerWithMeta(
        onTypeChangedListener: BiConsumer<EntityWithMeta<TypeDef>?, EntityWithMeta<TypeDef>?>
    ) {
        onTypeChangedListeners.add { before, after -> onTypeChangedListener.accept(before, after) }
    }

    override fun addOnDeletedListener(listener: (String) -> Unit) {
        onDeletedListeners.add(listener)
    }

    override fun getAll(): List<TypeDef> {
        return typeRepoDao.findAll(VoidPredicate.INSTANCE, 10000, 0, null)
            .map { typeConverter.toDto(it) }
    }

    override fun getAllWithMeta(): List<EntityWithMeta<TypeDef>> {
        return typeRepoDao.findAll(VoidPredicate.INSTANCE, 10000, 0, null)
            .map { typeConverter.toDtoWithMeta(it) }
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
        forEachTypeInDescHierarchy(typeRepoDao.findByExtId(id), action)
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

    override fun getAllWithMeta(typeIds: Collection<String>): List<EntityWithMeta<TypeDef>> {
        return typeRepoDao.findAllByExtIds(HashSet(typeIds)).map {
            typeConverter.toDtoWithMeta(it)
        }.toList()
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

    override fun getByIdWithMetaOrNull(typeId: String): EntityWithMeta<TypeDef>? {
        return typeRepoDao.findByExtId(typeId)?.let { typeConverter.toDtoWithMeta(it) }
    }

    override fun getByIdOrNull(typeId: String): TypeDef? {
        return typeRepoDao.findByExtId(typeId)?.let { typeConverter.toDto(it) }
    }

    override fun getOrCreateByExtId(typeId: String): TypeDef {

        val byExtId: TypeEntity? = typeRepoDao.findByExtId(typeId)
        if (byExtId != null) {
            return typeConverter.toDto(byExtId)
        }

        check(
            !(
                "base" == typeId ||
                    "user-base" == typeId ||
                    "type" == typeId
                )
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
        updateModifiedTimeForLinkedTypes(deleteImpl(typeId), asc = true, desc = false)
    }

    @Transactional
    override fun deleteWithChildren(typeId: String) {
        val children = typeRepoDao.getChildrenIds(typeId)
        children.forEach { childId ->
            deleteWithChildren(childId)
        }
        updateModifiedTimeForLinkedTypes(deleteImpl(typeId), asc = true, desc = false)
    }

    private fun deleteImpl(typeId: String): String {
        if (PROTECTED_TYPES.contains(typeId)) {
            throw RuntimeException("Type '$typeId' is protected")
        }
        val typeEntity = typeRepoDao.findByExtId(typeId) ?: return ""
        if (typeRepoDao.getChildrenIds(typeId).isNotEmpty()) {
            error("Type $typeId contains children and can't be deleted")
        }
        val parentId = typeEntity.parent?.extId ?: ""
        typeRepoDao.delete(typeEntity)
        onDeletedListeners.forEach { it(typeId) }
        return parentId
    }

    @Transactional
    override fun save(dto: TypeDef): TypeDef {

        val typeDefBefore: EntityWithMeta<TypeDef>? = typeRepoDao.findByExtId(dto.id)?.let {
            typeConverter.toDtoWithMeta(it)
        }

        var entity = typeConverter.toEntity(dto)

        entity = typeRepoDao.save(entity)

        updateModifiedTimeForLinkedTypes(dto.id, asc = true, desc = true)

        val typeDefAfter = typeConverter.toDtoWithMeta(entity)
        onTypeChangedListeners.forEach {
            it.invoke(typeDefBefore, typeDefAfter)
        }

        return typeDefAfter.entity
    }

    private fun updateModifiedTimeForLinkedTypes(typeId: String, asc: Boolean, desc: Boolean) {
        if (typeId.isBlank()) {
            return
        }
        val types = HashSet<String>()
        val action: (TypeEntity) -> Unit = { typeEntity ->
            if (typeEntity.extId != typeId) {
                typeEntity.lastModifiedDate = Instant.now()
                typeRepoDao.save(typeEntity)
                types.add(typeEntity.extId)
            }
            types.add(typeEntity.extId)
        }
        if (desc) {
            forEachTypeInDescHierarchy(typeId, action)
        }
        if (asc) {
            forEachTypeInAscHierarchy(typeId, action)
        }
        onTypeHierarchyChangedListeners.forEach {
            it.invoke(types)
        }
    }
}
