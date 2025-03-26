package ru.citeck.ecos.model.domain.workspace.patch

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.data.sql.records.DbRecordsControlAtts
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceMemberDesc
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef
import ru.citeck.ecos.model.lib.attributes.dto.AttributeType
import ru.citeck.ecos.model.type.service.TypesService
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.txn.lib.TxnContext
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.patch.annotaion.EcosLocalPatch
import java.util.concurrent.Callable

@Component
@EcosLocalPatch("make-ws-member-authorities-as-multiple-att", "2025-02-10T00:00:00Z")
class MakeWsMemberAuthoritiesAsMultipleAtt(
    val recordsService: RecordsService,
    val typesService: TypesService
) : Callable<Any> {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun call(): Any {

        val workspacesToUpdate = recordsService.query(RecordsQuery.create()
            .withSourceId(WorkspaceMemberDesc.SOURCE_ID)
            .withQuery(Predicates.empty(WorkspaceMemberDesc.ATT_AUTHORITIES))
            .build(),
            WorkspaceMemberAtts::class.java
        ).getRecords()

        if (workspacesToUpdate.isEmpty()) {
            log.info { "Nothing to update" }
            return "[]"
        }

        log.info { "Workspaces to update: $workspacesToUpdate" }

        val memberType = typesService.getByIdOrNull(WorkspaceMemberDesc.TYPE_ID)
        if (memberType != null && memberType.model.attributes.all { it.id != WorkspaceMemberDesc.ATT_AUTHORITIES }) {
            // ensure authorities attribute exists
            log.info { "Add authorities attribute to type" }
            val updatedType = memberType.copy()
                .withModel(memberType.model.copy()
                    .withAttributes(
                        listOf(
                            *memberType.model.attributes.toTypedArray(),
                            AttributeDef.create()
                                .withId(WorkspaceMemberDesc.ATT_AUTHORITIES)
                                .withType(AttributeType.AUTHORITY)
                                .withMultiple(true)
                                .build()
                        )
                    ).build()
                ).build()
            typesService.save(updatedType)
        }

        for (workspace in workspacesToUpdate) {
            if (EntityRef.isNotEmpty(workspace.authority)) {
                TxnContext.doInNewTxn {
                    recordsService.mutate(
                        workspace.id,
                        mapOf(
                            WorkspaceMemberDesc.ATT_AUTHORITIES to listOf(workspace.authority),
                            DbRecordsControlAtts.DISABLE_AUDIT to true,
                            DbRecordsControlAtts.DISABLE_EVENTS to true
                        )
                    )
                }
            } else {
                log.warn { "Authority att is empty for ${workspace.id}" }
            }
        }

        log.info { "Updating completed" }
        return "[" + workspacesToUpdate.joinToString { "\"$it\""} + "]"
    }

    data class WorkspaceMemberAtts(
        val id: EntityRef,
        val authority: EntityRef?
    )
}
