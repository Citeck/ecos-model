package ru.citeck.ecos.model.type.service

import org.springframework.data.domain.Sort
import ru.citeck.ecos.model.type.dto.TypeDef
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records3.record.mixin.impl.mutmeta.MutMeta
import java.util.function.Consumer

interface TypeService {

    fun addListener(onTypeChangedListener: Consumer<TypeDef>)

    fun getAll(max: Int, skip: Int): List<TypeDef>

    fun getAll(max: Int, skip: Int, predicate: Predicate): List<TypeDef>

    fun getAll(): List<TypeDef>

    fun getAll(typeIds: Collection<String>): List<TypeDef>

    fun getAll(max: Int, skip: Int, predicate: Predicate, sort: Sort?): List<TypeDef>

    fun getById(typeId: String): TypeDef

    fun getByIdOrNull(typeId: String): TypeDef?

    fun getOrCreateByExtId(typeId: String): TypeDef

    fun getParentIds(id: String): List<String>

    fun getChildren(typeId: String): List<String>

    fun expandTypes(typeIds: Collection<String>): List<TypeDef>

    fun delete(typeId: String)

    fun save(dto: TypeDef): TypeDef

    fun getCount(predicate: Predicate): Long

    fun getCount(): Long
}
