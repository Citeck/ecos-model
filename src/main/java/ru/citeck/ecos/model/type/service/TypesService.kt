package ru.citeck.ecos.model.type.service

import ru.citeck.ecos.commons.data.entity.EntityWithMeta
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import java.util.function.BiConsumer
import java.util.function.Consumer

interface TypesService {

    fun addOnDeletedListener(listener: (TypeId) -> Unit)

    fun addListenerWithMeta(onTypeChangedListener: BiConsumer<EntityWithMeta<TypeDef>?, EntityWithMeta<TypeDef>?>)

    fun addListenerTypeHierarchyChangedListener(onTypeChangedListener: Consumer<Set<TypeId>>)

    fun addListener(order: Float, onTypeChangedListener: BiConsumer<TypeDef?, TypeDef?>)

    fun addListener(onTypeChangedListener: BiConsumer<TypeDef?, TypeDef?>)

    fun getAll(max: Int, skip: Int): List<TypeDef>

    fun getAllWithMeta(max: Int, skip: Int): List<EntityWithMeta<TypeDef>>

    fun getAll(max: Int, skip: Int, predicate: Predicate): List<TypeDef>

    fun getAll(): List<TypeDef>

    fun getAllWithMeta(): List<EntityWithMeta<TypeDef>>

    fun getAllWithMeta(typeIds: Collection<TypeId>): List<EntityWithMeta<TypeDef>>

    fun getAllWithMeta(max: Int, skip: Int, predicate: Predicate, sort: List<SortBy>): List<EntityWithMeta<TypeDef>>

    fun getAll(max: Int, skip: Int, predicate: Predicate, sort: List<SortBy>): List<TypeDef>

    fun getById(typeId: TypeId): TypeDef

    fun getByIdWithMetaOrNull(typeId: TypeId): EntityWithMeta<TypeDef>?

    fun getByIdOrNull(typeId: TypeId): TypeDef?

    fun getOrCreateByExtId(typeId: TypeId): TypeDef

    fun getParentIds(id: TypeId): List<TypeId>

    fun getChildren(typeId: TypeId): List<TypeId>

    fun expandTypes(typeIds: Collection<TypeId>): List<TypeDef>

    fun getInhAttributes(typeId: TypeId): List<AttributeDef>

    fun delete(typeId: TypeId)

    /**
     * Delete type with its children
     */
    fun deleteWithChildren(typeId: TypeId)

    fun save(dto: TypeDef): TypeDef

    fun save(types: List<TypeDef>): List<TypeDef>

    fun save(dto: TypeDef, clonedRecord: Boolean): TypeDef

    fun getCount(predicate: Predicate): Long

    fun getCount(): Long
}
