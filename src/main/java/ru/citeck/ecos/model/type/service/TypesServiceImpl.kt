package ru.citeck.ecos.model.type.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.entity.EntityWithMeta
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.model.lib.workspace.IdInWs
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
        private const val VALID_ID_PATTERN_TXT = "^[\\w\$:/.-]+\\w\$"
        private val VALID_ID_PATTERN = Pattern.compile(VALID_ID_PATTERN_TXT)
    }

    private var onTypeChangedListeners = CopyOnWriteArrayList<TypeDefListener>()

    private var onDeletedListeners: MutableList<(EntityWithMeta<TypeDef>) -> Unit> = CopyOnWriteArrayList()

    private var onTypeHierarchyChangedListeners: MutableList<(Set<IdInWs>) -> Unit> = CopyOnWriteArrayList()

    override fun getChildren(typeId: IdInWs): List<IdInWs> {
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

    override fun addListenerTypeHierarchyChangedListener(onTypeChangedListener: Consumer<Set<IdInWs>>) {
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

    override fun addOnDeletedListener(listener: (EntityWithMeta<TypeDef>) -> Unit) {
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

    override fun getParentIds(id: IdInWs): List<IdInWs> {

        var typeEntity = typeRepoDao.findByExtId(id)
        val parents = mutableListOf<IdInWs>()
        while (typeEntity != null) {
            parents.add(typeEntity.getTypeId())
            typeEntity = typeEntity.parent
        }
        if (parents.isEmpty() || parents.last().id != "base") {
            parents.add(IdInWs.create("base"))
        }
        return parents
    }

    override fun expandTypes(typeIds: Collection<IdInWs>): List<TypeDef> {
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

    override fun getInhAttributes(typeId: IdInWs): List<AttributeDef> {
        if (typeId.isEmpty()) {
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

    private fun forEachTypeInDescHierarchy(
        id: IdInWs,
        filter: (TypeEntity) -> Boolean,
        action: (TypeEntity) -> Unit
    ) {
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
        val childrenIds = getChildren(type.getTypeId())
        for (childId in childrenIds) {
            forEachTypeInDescHierarchy(childId, filter, action)
        }
    }

    private fun <T : Any> forEachTypeInAscHierarchy(
        typeId: IdInWs,
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

    override fun getAllWithMeta(typeIds: Collection<IdInWs>): List<EntityWithMeta<TypeDef>> {
        return typeRepoDao.findAllByTypeIds(typeIds)
            .map { typeConverter.toDtoWithMeta(it) }
    }

    override fun getById(typeId: IdInWs): TypeDef {
        return typeRepoDao.findByExtId(typeId)?.let { typeConverter.toDto(it) }
            ?: error("Type is not found: '$typeId'")
    }

    override fun getByIdWithMetaOrNull(typeId: IdInWs): EntityWithMeta<TypeDef>? {
        return typeRepoDao.findByExtId(typeId)?.let { typeConverter.toDtoWithMeta(it) }
    }

    override fun getByIdOrNull(typeId: IdInWs): TypeDef? {
        return typeRepoDao.findByExtId(typeId)?.let { typeConverter.toDto(it) }
    }

    @Transactional
    override fun getOrCreateByExtId(typeId: IdInWs): TypeDef {

        val byExtId: TypeEntity? = typeRepoDao.findByExtId(typeId)
        if (byExtId != null) {
            return typeConverter.toDto(byExtId)
        }

        check(
            !(
                "base" == typeId.id ||
                    "user-base" == typeId.id ||
                    "type" == typeId.id
                )
        ) {
            "Base type doesn't exists: '$typeId'"
        }

        val typeDef = TypeDef.create()
        typeDef.withId(typeId.id)
        typeDef.withParentRef(ModelUtils.getTypeRef("user-base"))
        typeDef.withName(MLText(typeId.id))

        return save(typeDef.build())
    }

    @Transactional
    override fun delete(typeId: IdInWs) {
        fireOnTypeHierarchyChangedEvent(deleteImpl(typeId), asc = true, desc = false)
    }

    @Transactional
    override fun deleteWithChildren(typeId: IdInWs) {
        val children = typeRepoDao.getChildrenIds(typeId)
        children.forEach { childId ->
            deleteWithChildren(childId)
        }
        fireOnTypeHierarchyChangedEvent(deleteImpl(typeId), asc = true, desc = false)
    }

    private fun deleteImpl(typeId: IdInWs): IdInWs {
        if (PROTECTED_TYPES.contains(typeId.id)) {
            throw RuntimeException("Type '$typeId' is protected")
        }
        val typeEntity = typeRepoDao.findByExtId(typeId) ?: return IdInWs.EMPTY
        if (typeRepoDao.getChildrenIds(typeId).isNotEmpty()) {
            error("Type $typeId contains children and can't be deleted")
        }
        val typeDef = typeConverter.toDtoWithMeta(typeEntity)
        val parentId = typeEntity.parent?.getTypeId() ?: IdInWs.EMPTY
        typeRepoDao.delete(typeEntity)
        onDeletedListeners.forEach { it(typeDef) }
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
        val typesToHierarchyChangedEvent = HashSet<IdInWs>()
        for (type in types) {
            val typeAfterSave = saveTypeDefImpl(type, clonedRecord)
            typesToHierarchyChangedEvent.add(typeAfterSave.getTypeId())
            result.add(typeAfterSave)
        }

        fireOnTypeHierarchyChangedEvent(typesToHierarchyChangedEvent, asc = true, desc = true)

        return result
    }

    private fun saveTypeDefImpl(dto: TypeDef, clonedRecord: Boolean): TypeDef {

        val existingEntity = typeRepoDao.findByExtId(dto.getTypeId())

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
            error("Invalid type id: '${dto.id}'. Valid name pattern: '$VALID_ID_PATTERN_TXT'")
        }

        var entity = typeConverter.toEntity(dto, existingEntity)

        entity = typeRepoDao.save(entity)

        val typeDefAfter = typeConverter.toDtoWithMeta(entity)

        onTypeChangedListeners.forEach {
            it.action.invoke(typeDefBefore, typeDefAfter)
        }

        return typeDefAfter.entity
    }

    private fun fireOnTypeHierarchyChangedEvent(typeId: IdInWs, asc: Boolean, desc: Boolean) {
        if (typeId.isEmpty()) {
            return
        }
        return fireOnTypeHierarchyChangedEvent(listOf(typeId), asc, desc)
    }

    private fun fireOnTypeHierarchyChangedEvent(typesId: Collection<IdInWs>, asc: Boolean, desc: Boolean) {
        if (typesId.isEmpty()) {
            return
        }
        val types = HashSet<IdInWs>()
        val action: (TypeEntity) -> Unit = { typeEntity ->
            types.add(typeEntity.getTypeId())
        }
        if (desc) {
            val visited = HashSet<IdInWs>()
            for (typeId in typesId) {
                forEachTypeInDescHierarchy(typeId, { visited.add(it.getTypeId()) }, action)
            }
        }
        if (asc) {
            val visited = HashSet<IdInWs>()
            for (typeId in typesId) {
                forEachTypeInAscHierarchy(typeId, {
                    it.workspace == typeId.workspace && visited.add(it.getTypeId())
                }, action)
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
