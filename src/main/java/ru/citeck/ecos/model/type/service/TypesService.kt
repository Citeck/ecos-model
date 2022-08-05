package ru.citeck.ecos.model.type.service

import org.springframework.data.domain.Sort
import ru.citeck.ecos.commons.data.entity.EntityWithMeta
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import java.util.function.BiConsumer
import java.util.function.Consumer

interface TypesService {

    fun addOnDeletedListener(listener: (String) -> Unit)

    fun addListenerWithMeta(onTypeChangedListener: BiConsumer<EntityWithMeta<TypeDef>?, EntityWithMeta<TypeDef>?>)

    fun addListenerTypeHierarchyChangedListener(onTypeChangedListener: Consumer<Set<String>>)

    fun addListener(onTypeChangedListener: BiConsumer<TypeDef?, TypeDef?>)

    fun getAll(max: Int, skip: Int): List<TypeDef>

    fun getAll(max: Int, skip: Int, predicate: Predicate): List<TypeDef>

    fun getAll(): List<TypeDef>

    fun getAllWithMeta(): List<EntityWithMeta<TypeDef>>

    fun getAll(typeIds: Collection<String>): List<TypeDef>

    fun getAllWithMeta(typeIds: Collection<String>): List<EntityWithMeta<TypeDef>>

    fun getAll(max: Int, skip: Int, predicate: Predicate, sort: Sort?): List<TypeDef>

    fun getById(typeId: String): TypeDef

    fun getByIdWithMetaOrNull(typeId: String): EntityWithMeta<TypeDef>?

    fun getByIdOrNull(typeId: String): TypeDef?

    fun getOrCreateByExtId(typeId: String): TypeDef

    fun getParentIds(id: String): List<String>

    fun getChildren(typeId: String): List<String>

    fun expandTypes(typeIds: Collection<String>): List<TypeDef>

    fun getInhAttributes(typeId: String): List<AttributeDef>

    fun delete(typeId: String)

    /**
     * Delete type with it's children
     */
    fun deleteWithChildren(typeId: String)

    fun save(dto: TypeDef): TypeDef

    fun getCount(predicate: Predicate): Long

    fun getCount(): Long
}
