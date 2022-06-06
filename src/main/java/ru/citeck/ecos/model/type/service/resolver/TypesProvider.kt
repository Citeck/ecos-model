package ru.citeck.ecos.model.type.service.resolver

import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef

interface TypesProvider {

    fun get(id: String): TypeDef?

    fun getChildren(typeId: String): List<String>
}
