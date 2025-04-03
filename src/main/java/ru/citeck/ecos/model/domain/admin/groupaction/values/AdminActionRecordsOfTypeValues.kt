package ru.citeck.ecos.model.domain.admin.groupaction.values

import com.google.common.collect.Iterators
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.groupactions.values.GroupActionValues
import ru.citeck.ecos.groupactions.values.GroupActionValuesFactory
import ru.citeck.ecos.groupactions.values.records.RecordsQueryValuesFactory
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.type.registry.EcosTypesRegistry

class AdminActionRecordsOfTypeValues(
    recordsService: RecordsService,
    private val typesRegistry: EcosTypesRegistry
) : GroupActionValuesFactory<AdminActionRecordsOfTypeValues.Config> {

    private val recsQueryValuesFactory = RecordsQueryValuesFactory(recordsService)

    override fun getValues(
        config: Config,
        attributes: Map<String, String>
    ): GroupActionValues<*> {

        if (AuthContext.isNotRunAsSystemOrAdmin()) {
            error("Permission denied")
        }
        typesRegistry.getValue(config.typeRef.getLocalId())
            ?: error("Type is not registered: ${config.typeRef}")

        val valuesBySourceId = LinkedHashMap<String, GroupActionValues<Any>>()

        val workspaces = config.workspaces.map {
            it.removePrefix(AppName.EMODEL + "/" + WorkspaceDesc.SOURCE_ID + "@")
        }

        fun registerValuesForTypeRef(typeRef: EntityRef) {
            val typeDef = typesRegistry.getValue(typeRef.getLocalId()) ?: return
            if (valuesBySourceId.containsKey(typeDef.sourceId)) {
                return
            }
            val query = RecordsQuery.create()
                .withSourceId(typeDef.sourceId)
                .withQuery(config.predicate)
                .withEcosType(typeDef.id)
                .withWorkspaces(workspaces)
                .build()
            valuesBySourceId[typeDef.sourceId] = recsQueryValuesFactory.getValues(
                RecordsQueryValuesFactory.Config(query),
                attributes
            )
            for (childRef in typesRegistry.getChildren(typeRef)) {
                registerValuesForTypeRef(childRef)
            }
        }
        registerValuesForTypeRef(config.typeRef)

        return GroupActionValuesImpl(valuesBySourceId.values.toList())
    }

    override fun getType(): String {
        return "admin-action-records-of-type"
    }

    class Config(
        val typeRef: EntityRef,
        val workspaces: List<String> = emptyList(),
        val predicate: Predicate = Predicates.alwaysTrue()
    )

    private class GroupActionValuesImpl(
        val innerValues: List<GroupActionValues<Any>>
    ) : GroupActionValues<Any> {

        override fun iterator(): Iterator<Any> {
            val iterator = RunAsSystemIterator(
                Iterators.concat(*innerValues.map { it.iterator() }.toTypedArray())
            )
            return iterator
        }

        override fun getTotalCount(): Long {
            return AuthContext.runAsSystem {
                innerValues.sumOf { it.getTotalCount() }
            }
        }

        private class RunAsSystemIterator<T>(
            private val impl: Iterator<T>
        ) : Iterator<T> {
            override fun hasNext(): Boolean {
                return AuthContext.runAsSystem { impl.hasNext() }
            }
            override fun next(): T {
                return AuthContext.runAsSystem { impl.next() }
            }
        }
    }
}
