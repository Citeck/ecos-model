package ru.citeck.ecos.model.domain.doclib

import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.webapp.api.entity.EntityRef

class DocLibChildrenQuery(
    val parentRef: EntityRef? = null,
    val filter: Predicate,
    val nodeType: DocLibNodeType? = null
)
