package ru.citeck.ecos.model.domain.doclib

import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.model.VoidPredicate
import ru.citeck.ecos.records3.record.atts.value.AttValue
import ru.citeck.ecos.records3.record.atts.value.impl.AttValueDelegate
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.webapp.api.entity.EntityRef

class DocLibRecord(
    private val id: EntityRef,
    innerAttValue: AttValue,
    private val nodeType: DocLibNodeType,
    private val parent: EntityRef,
    private val docLibRecords: DocLibRecords
) : AttValueDelegate(innerAttValue) {

    companion object {
        const val ATT_NODE_TYPE = "nodeType"
        const val ATT_PATH = "path"
        const val ATT_CHILDREN = "children"
        const val ATT_HAS_CHILDREN_DIRS = "hasChildrenDirs"
    }

    override fun getId(): Any {
        return id
    }

    override fun getAtt(name: String): Any? {
        return when (name) {
            RecordConstants.ATT_PARENT -> parent
            ATT_PATH -> docLibRecords.getPath(id)
            ATT_CHILDREN -> {
                val query = DocLibChildrenQuery(
                    parentRef = id,
                    nodeType = null,
                    filter = VoidPredicate.INSTANCE
                )
                docLibRecords.getChildren(
                    query,
                    listOf(
                        SortBy("_name", true)
                    ),
                    0,
                    -1,
                    emptyList()
                )
            }
            ATT_NODE_TYPE -> nodeType
            ATT_HAS_CHILDREN_DIRS -> docLibRecords.hasChildrenDirs(id, emptyList())
            else -> super.getAtt(name)
        }
    }
}
