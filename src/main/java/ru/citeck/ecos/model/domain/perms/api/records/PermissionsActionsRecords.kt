package ru.citeck.ecos.model.domain.perms.api.records

import mu.KotlinLogging
import org.springframework.security.access.annotation.Secured
import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.data.sql.records.DbRecordsControlAtts
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.iter.IterableRecords
import ru.citeck.ecos.records3.iter.IterableRecordsConfig
import ru.citeck.ecos.records3.iter.PageStrategy
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.mutate.ValueMutateDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.txn.lib.TxnContext
import ru.citeck.ecos.webapp.api.entity.EntityRef
import kotlin.concurrent.thread

@Component
class PermissionsActionsRecords : AbstractRecordsDao(), ValueMutateDao<PermissionsActionsRecords.ActionDto> {

    companion object {
        const val ID = "update-permissions"

        private val log = KotlinLogging.logger {}
    }

    @Secured(AuthRole.ADMIN, AuthRole.SYSTEM)
    override fun mutate(value: ActionDto): String {
        if (value.recordRef.isNotEmpty()) {
            AuthContext.runAsSystem {
                recordsService.mutateAtt(value.recordRef, DbRecordsControlAtts.UPDATE_PERMISSIONS, true)
            }
        } else {
            thread(start = true) {
                log.info { "Start permissions update for type ${value.typeRef}" }
                AuthContext.runAsSystem {
                    val records = IterableRecords(
                        RecordsQuery.create()
                            .withEcosType(value.typeRef.getLocalId())
                            .withQuery(Predicates.eq(RecordConstants.ATT_TYPE, value.typeRef))
                            .build(),
                        IterableRecordsConfig(100, PageStrategy.CREATED, emptyMap<String, String>()),
                        recordsService
                    ).iterator()
                    var processedCount = 0
                    var reportCounter = 0
                    while (records.hasNext()) {
                        TxnContext.doInNewTxn {
                            recordsService.mutateAtt(records.next(), DbRecordsControlAtts.UPDATE_PERMISSIONS, true)
                        }
                        processedCount++
                        if (processedCount / 1000 > reportCounter) {
                            reportCounter++
                            log.info { "Permissions updated for $processedCount records with type ${value.typeRef}" }
                        }
                    }
                }
                log.info { "Permissions updated for type ${value.typeRef}" }
            }
        }
        log.info { "Permissions updating started for type ${value.typeRef}" }
        return ""
    }

    override fun getId(): String {
        return ID
    }

    class ActionDto(
        val recordRef: EntityRef = EntityRef.EMPTY,
        val typeRef: EntityRef = EntityRef.EMPTY
    )
}
