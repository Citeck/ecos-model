package ru.citeck.ecos.model.type.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.entity.EntityWithMeta
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.model.lib.workspace.WsScopedArtifactUtils
import ru.citeck.ecos.model.type.converter.TypeConverter
import ru.citeck.ecos.model.type.repository.TypeEntity
import ru.citeck.ecos.model.type.service.dao.TypeRepoDao
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.VoidPredicate
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.txn.lib.TxnContext
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.regex.Pattern

@Service
class TypesServiceImpl(
    private val typeConverter: TypeConverter,
    private val typeRepoDao: TypeRepoDao
) : TypesService {

    companion object {
        private val PROTECTED_TYPES: Set<String> = setOf(
            "base",
            "case",
            "document",
            "number-template",
            "type",
            "user-base",
            "file",
            "directory",
            "doclib-directory",
            "doclib-file"
        )
        private const val VALID_ID_PATTERN_TXT = "^[\\w\$/.-]+\\w\$"
        private val VALID_ID_PATTERN = Pattern.compile(VALID_ID_PATTERN_TXT)
    }

    private var onTypeChangedListeners = CopyOnWriteArrayList<TypeDefListener>()

    private var onDeletedListeners: MutableList<(String) -> Unit> = CopyOnWriteArrayList()

    private var onTypeHierarchyChangedListeners: MutableList<(Set<String>) -> Unit> = CopyOnWriteArrayList()

    override fun getChildren(typeId: String): List<String> {
        return typeRepoDao.getChildrenIds(typeId).toList()
    }

    override fun getAll(max: Int, skip: Int, predicate: Predicate): List<TypeDef> {
        return getAll(max, skip, predicate, emptyList())
    }

    override fun getAll(max: Int, skip: Int, predicate: Predicate, sort: List<SortBy>): List<TypeDef> {
        if (max <= 0) {
            return emptyList()
        }
        return typeRepoDao.findAll(predicate, max, skip, sort)
            .map { typeConverter.toDto(it) }
            .toList()
    }

    override fun getAllWithMeta(
        max: Int,
        skip: Int,
        predicate: Predicate,
        sort: List<SortBy>
    ): List<EntityWithMeta<TypeDef>> {
        if (max <= 0) {
            return emptyList()
        }
        return typeRepoDao.findAll(predicate, max, skip, sort)
            .map { typeConverter.toDtoWithMeta(it) }
            .toList()
    }

    override fun getAll(max: Int, skip: Int): List<TypeDef> {
        if (max <= 0) {
            return emptyList()
        }
        return typeRepoDao.findAll(VoidPredicate.INSTANCE, max, skip, emptyList())
            .map { typeConverter.toDto(it) }
            .toList()
    }

    override fun getAllWithMeta(max: Int, skip: Int): List<EntityWithMeta<TypeDef>> {
        if (max <= 0) {
            return emptyList()
        }
        return typeRepoDao.findAll(VoidPredicate.INSTANCE, max, skip, emptyList())
            .map { typeConverter.toDtoWithMeta(it) }
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

    override fun addListener(order: Float, onTypeChangedListener: BiConsumer<TypeDef?, TypeDef?>) {
        onTypeChangedListeners.add(
            TypeDefListener(order) { before, after ->
                onTypeChangedListener.accept(before?.entity, after?.entity)
            }
        )
        onTypeChangedListeners.sort()
    }

    override fun addListener(onTypeChangedListener: BiConsumer<TypeDef?, TypeDef?>) {
        addListener(0f, onTypeChangedListener)
    }

    fun addListenerWithMeta(
        order: Float,
        onTypeChangedListener: BiConsumer<EntityWithMeta<TypeDef>?, EntityWithMeta<TypeDef>?>
    ) {
        onTypeChangedListeners.add(
            TypeDefListener(order) { before, after ->
                onTypeChangedListener.accept(before, after)
            }
        )
        onTypeChangedListeners.sort()
    }

    override fun addListenerWithMeta(
        onTypeChangedListener: BiConsumer<EntityWithMeta<TypeDef>?, EntityWithMeta<TypeDef>?>
    ) {
        addListenerWithMeta(0f, onTypeChangedListener)
    }

    override fun addOnDeletedListener(listener: (String) -> Unit) {
        onDeletedListeners.add(listener)
    }

    override fun getAll(): List<TypeDef> {
        return typeRepoDao.findAll(VoidPredicate.INSTANCE, 10000, 0, emptyList())
            .map { typeConverter.toDto(it) }
    }

    override fun getAllWithMeta(): List<EntityWithMeta<TypeDef>> {
        return typeRepoDao.findAll(VoidPredicate.INSTANCE, 10000, 0, emptyList())
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
            forEachTypeInDescHierarchy(typeId, { resultIdsSet.add(it.extId) }) { type ->
                result.add(typeConverter.toDto(type))
            }
        }
        return result
    }

    override fun getInhAttributes(typeId: String): List<AttributeDef> {
        if (typeId.isBlank()) {
            return emptyList()
        }
        val attributesHierarchy = mutableListOf<List<AttributeDef>>()
        forEachTypeInAscHierarchy(typeId, { true }) { typeEntity ->
            val model = Json.mapper.read(typeEntity.model, TypeModelDef::class.java)
            if (model != null) {
                attributesHierarchy.add(model.attributes)
            }
        }
        val attributes = mutableMapOf<String, AttributeDef>()
        for (i in attributesHierarchy.lastIndex downTo 0) {
            attributesHierarchy[i].forEach {
                attributes[it.id] = it
            }
        }
        return attributes.values.toList()
    }

    private fun forEachTypeInDescHierarchy(id: String, filter: (TypeEntity) -> Boolean, action: (TypeEntity) -> Unit) {
        forEachTypeInDescHierarchy(typeRepoDao.findByExtId(id), filter, action)
    }

    private fun forEachTypeInDescHierarchy(
        type: TypeEntity?,
        filter: (TypeEntity) -> Boolean,
        action: (TypeEntity) -> Unit
    ) {
        if (type == null || !filter(type)) {
            return
        }
        action.invoke(type)
        val childrenIds = getChildren(type.extId)
        for (childId in childrenIds) {
            forEachTypeInDescHierarchy(childId, filter, action)
        }
    }

    private fun <T : Any> forEachTypeInAscHierarchy(
        typeId: String,
        filter: (TypeEntity) -> Boolean,
        action: (TypeEntity) -> T?
    ): T? {
        var typeEntity = typeRepoDao.findByExtId(typeId)
        while (typeEntity != null && filter(typeEntity)) {
            action.invoke(typeEntity)
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

    @Transactional
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
        typeDef.withParentRef(ModelUtils.getTypeRef("user-base"))
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
        fireOnTypeHierarchyChangedEvent(deleteImpl(typeId), asc = true, desc = false)
    }

    @Transactional
    override fun deleteWithChildren(typeId: String) {
        val children = typeRepoDao.getChildrenIds(typeId)
        children.forEach { childId ->
            deleteWithChildren(childId)
        }
        fireOnTypeHierarchyChangedEvent(deleteImpl(typeId), asc = true, desc = false)
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
        return save(dto, false)
    }

    @Transactional
    override fun save(dto: TypeDef, clonedRecord: Boolean): TypeDef {
        return TxnContext.doInTxn { save(listOf(dto), clonedRecord).first() }
    }

    @Transactional
    override fun save(types: List<TypeDef>): List<TypeDef> {
        return TxnContext.doInTxn { save(types, false) }
    }

    private fun save(types: List<TypeDef>, clonedRecord: Boolean): List<TypeDef> {

        if (types.isEmpty()) {
            return emptyList()
        }

        val result = ArrayList<TypeDef>()
        val typesToHierarchyChangedEvent = HashSet<String>()
        for (type in types) {
            val typeAfterSave = saveTypeDefImpl(type, clonedRecord)
            typesToHierarchyChangedEvent.add(typeAfterSave.id)
            result.add(typeAfterSave)
        }

        fireOnTypeHierarchyChangedEvent(typesToHierarchyChangedEvent, asc = true, desc = true)

        return result
    }

    private fun saveTypeDefImpl(dto: TypeDef, clonedRecord: Boolean): TypeDef {

        val existingEntity = typeRepoDao.findByExtId(dto.id)

        val typeDefBefore: EntityWithMeta<TypeDef>? = existingEntity?.let {
            typeConverter.toDtoWithMeta(it)
        }
        if (typeDefBefore != null) {
            if (clonedRecord) {
                error("Type with id '${dto.id}' already exists")
            }
            if (typeDefBefore.entity == dto) {
                // nothing changed
                return typeDefBefore.entity
            }
        } else if (!VALID_ID_PATTERN.matcher(dto.id).matches()) {
            val idToError = if (dto.workspace.isNotBlank()) {
                WsScopedArtifactUtils.removeWsPrefixFromId(dto.id)
            } else {
                dto.id
            }
            error("Invalid type id: '$idToError'. Valid name pattern: '$VALID_ID_PATTERN_TXT'")
        }

        var entity = typeConverter.toEntity(dto, existingEntity)

        entity = typeRepoDao.save(entity)

        val typeDefAfter = typeConverter.toDtoWithMeta(entity)

        onTypeChangedListeners.forEach {
            it.action.invoke(typeDefBefore, typeDefAfter)
        }

        return typeDefAfter.entity
    }

    private fun fireOnTypeHierarchyChangedEvent(typeId: String, asc: Boolean, desc: Boolean) {
        if (typeId.isBlank()) {
            return
        }
        return fireOnTypeHierarchyChangedEvent(listOf(typeId), asc, desc)
    }

    private fun fireOnTypeHierarchyChangedEvent(typesId: Collection<String>, asc: Boolean, desc: Boolean) {
        if (typesId.isEmpty()) {
            return
        }
        val types = HashSet<String>()
        val action: (TypeEntity) -> Unit = { typeEntity ->
            types.add(typeEntity.extId)
        }
        if (desc) {
            val visited = HashSet<String>()
            for (typeId in typesId) {
                forEachTypeInDescHierarchy(typeId, { visited.add(it.extId) }, action)
            }
        }
        if (asc) {
            val visited = HashSet<String>()
            for (typeId in typesId) {
                forEachTypeInAscHierarchy(typeId, { visited.add(it.extId) }, action)
            }
        }
        onTypeHierarchyChangedListeners.forEach {
            it.invoke(types)
        }
    }

    private class TypeDefListener(
        val order: Float,
        val action: (
            EntityWithMeta<TypeDef>?,
            EntityWithMeta<TypeDef>?
        ) -> Unit
    ) : Comparable<TypeDefListener> {

        override fun compareTo(other: TypeDefListener): Int {
            return order.compareTo(other.order)
        }
    }
}
