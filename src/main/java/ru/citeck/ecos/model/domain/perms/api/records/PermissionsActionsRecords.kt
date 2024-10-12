package ru.citeck.ecos.model.domain.perms.api.records

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.access.annotation.Secured
import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.data.sql.records.DbRecordsControlAtts
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.iter.IterableRecordRefs
import ru.citeck.ecos.records3.iter.IterableRecordsConfig
import ru.citeck.ecos.records3.iter.PageStrategy
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
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

        private const val BATCH_SIZE = 10
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
                var processedCount = 0
                AuthContext.runAsSystem {
                    var query: Predicate = Predicates.eq(RecordConstants.ATT_TYPE, value.typeRef)
                    if (value.predicate != Predicates.alwaysTrue()) {
                        query = Predicates.and(query, value.predicate)
                    }
                    val records = IterableRecordRefs(
                        RecordsQuery.create()
                            .withEcosType(value.typeRef.getLocalId())
                            .withQuery(query)
                            .build(),
                        IterableRecordsConfig(100, PageStrategy.CREATED, emptyMap<String, String>()),
                        recordsService
                    ).iterator()
                    var reportCounter = 0
                    while (records.hasNext()) {
                        val batch = ArrayList<EntityRef>()
                        batch.add(records.next())
                        while (batch.size < BATCH_SIZE && records.hasNext()) {
                            batch.add(records.next())
                        }
                        TxnContext.doInNewTxn {
                            val mutateAtts = batch.map {
                                val atts = RecordAtts(it)
                                atts[DbRecordsControlAtts.UPDATE_PERMISSIONS] = true
                                atts
                            }
                            recordsService.mutate(mutateAtts)
                        }
                        processedCount += batch.size
                        if (processedCount / 1000 > reportCounter) {
                            reportCounter++
                            log.info { "Permissions updated for $processedCount records with type ${value.typeRef}" }
                        }
                    }
                }
                log.info {
                    "Permissions updating finished. " +
                        "Processed $processedCount records with type ${value.typeRef}"
                }
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
        val typeRef: EntityRef = EntityRef.EMPTY,
        val predicate: Predicate = Predicates.alwaysTrue()
    )
}
