package ru.citeck.ecos.model.domain.authorities.patch

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.data.sql.records.DbRecordsControlAtts
import ru.citeck.ecos.model.domain.authorities.constant.PersonConstants
import ru.citeck.ecos.model.domain.workspace.utils.WorkspaceSystemIdUtils
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records2.predicate.model.ValuePredicate
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.patch.annotaion.EcosLocalPatch
import java.time.Instant
import java.util.concurrent.Callable

@Component
@EcosLocalPatch("fill-ws-sys-id-for-persons", "2026-05-18T00:00:03Z", afterStart = true)
class FillWsSysIdForPersons(
    val recordsService: RecordsService
) : Callable<Any> {

    companion object {
        private val log = KotlinLogging.logger {}
        private const val BATCH_SIZE = 100
        private const val LOG_CHUNK_SIZE = 500
    }

    override fun call(): Any {

        val updatedChunk = mutableListOf<String>()
        var totalUpdated = 0
        var cursor: Instant = Instant.EPOCH

        fun flushUpdatedChunk(force: Boolean = false) {
            if (updatedChunk.isEmpty() || (!force && updatedChunk.size < LOG_CHUNK_SIZE)) {
                return
            }
            log.info { "Updated persons (total $totalUpdated): $updatedChunk" }
            updatedChunk.clear()
        }

        while (true) {
            val predicate = Predicates.and(
                Predicates.empty(PersonConstants.ATT_WS_SYS_ID),
                ValuePredicate.gt(RecordConstants.ATT_CREATED, cursor.toString())
            )

            val page = recordsService.query(
                RecordsQuery.create()
                    .withSourceId(AuthorityType.PERSON.sourceId)
                    .withQuery(predicate)
                    .withMaxItems(BATCH_SIZE)
                    .withSortBy(SortBy(RecordConstants.ATT_CREATED, true))
                    .build(),
                PersonPageAtts::class.java
            ).getRecords()

            if (page.isEmpty()) {
                break
            }

            log.info { "Processing batch of ${page.size} persons after $cursor" }

            for (person in page) {
                val userName = person.id.getLocalId()
                val userWsSysId = WorkspaceSystemIdUtils.createId(userName)
                if (userWsSysId != userName) {
                    AuthContext.runAsSystem {
                        recordsService.mutate(
                            person.id,
                            mapOf(
                                PersonConstants.ATT_WS_SYS_ID to userWsSysId,
                                DbRecordsControlAtts.DISABLE_EVENTS to true,
                                DbRecordsControlAtts.DISABLE_AUDIT to true
                            )
                        )
                    }
                    updatedChunk.add(userName)
                    totalUpdated++
                    flushUpdatedChunk()
                }
            }

            cursor = page.last().created
                ?: error("Created date is missing for person '${page.last().id}'")
        }

        flushUpdatedChunk(force = true)

        log.info { "Updating completed. Updated persons: $totalUpdated" }
        return totalUpdated
    }

    private class PersonPageAtts(
        val id: EntityRef,
        @AttName(RecordConstants.ATT_CREATED)
        val created: Instant?
    )
}
