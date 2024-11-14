package ru.citeck.ecos.model.domain.workspace.api.records

import com.github.benmanes.caffeine.cache.Caffeine
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceDesc
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceVisitDesc
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.mutate.ValueMutateDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ArrayBlockingQueue

@Component
class WorkspaceVisitedAction : AbstractRecordsDao(), ValueMutateDao<WorkspaceVisitedAction.VisitActionDto> {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    private val queueToProcess = ArrayBlockingQueue<ActionToProc>(1000)
    @Volatile
    private var lastLoggedWarnTime = 0L

    private val workspaceExistsCache = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterAccess(Duration.ofMinutes(5))
        .build<String, Boolean> {
            recordsService.getAtt(
                WorkspaceDesc.getRef(it),
                RecordConstants.ATT_NOT_EXISTS + ScalarType.BOOL_SCHEMA
            ).asBoolean().not()
        }

    @PostConstruct
    fun init() {
        val procThread = Thread.ofPlatform().unstarted {
            AuthContext.runAsSystem {
                while (true) {
                    val action = queueToProcess.take()
                    try {
                        processImpl(action)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw e
                    } catch (e: Throwable) {
                        log.error(e) { "Error while action processing: $action" }
                    }
                }
            }
        }
        procThread.start()
        Runtime.getRuntime().addShutdownHook(
            Thread.ofPlatform().unstarted {
                procThread.interrupt()
            }
        )
    }

    override fun mutate(value: VisitActionDto): Any? {
        val actionToProc = ActionToProc(
            value.workspace,
            AuthContext.getCurrentUser(),
            System.currentTimeMillis()
        )
        if (!queueToProcess.offer(actionToProc)) {
            if (System.currentTimeMillis() - lastLoggedWarnTime > 120_000) {
                log.warn { "Queue to process is full. visit action won't be processed: $actionToProc" }
                lastLoggedWarnTime = System.currentTimeMillis()
            }
        }
        return null
    }

    private fun processImpl(action: ActionToProc) {
        if (!workspaceExistsCache.get(action.workspace)) {
            log.warn {
                "Workspace doesn't exists: ${action.workspace}. " +
                    "Visit won't be registered for ${action.user}"
            }
            return
        }
        val userRef = AuthorityType.PERSON.getRef(action.user)

        val visitAtts = recordsService.queryOne(
            RecordsQuery.create()
                .withSourceId(WorkspaceVisitDesc.SOURCE_ID)
                .withQuery(Predicates.and(
                    Predicates.eq(WorkspaceVisitDesc.ATT_USER, userRef),
                    Predicates.eq(WorkspaceVisitDesc.ATT_WORKSPACE, action.workspace)
                )).build(),
            VisitAtts::class.java
        )
        val mutAtts = if (visitAtts == null) {
            val mutAtts = RecordAtts(EntityRef.create(WorkspaceVisitDesc.SOURCE_ID, ""))
            mutAtts[WorkspaceVisitDesc.ATT_VISITS_COUNT] = 1
            mutAtts[WorkspaceVisitDesc.ATT_WORKSPACE] = action.workspace
            mutAtts[WorkspaceVisitDesc.ATT_USER] = userRef
            mutAtts
        } else {
            val mutAtts = RecordAtts(visitAtts.id)
            mutAtts[WorkspaceVisitDesc.ATT_VISITS_COUNT] = visitAtts.visitsCount + 1
            mutAtts
        }
        mutAtts[WorkspaceVisitDesc.ATT_LAST_VISIT_TIME] = Instant.ofEpochMilli(action.time)
        recordsService.mutate(mutAtts)
    }

    override fun getId(): String {
        return "workspace-visited-action"
    }

    private class VisitAtts(
        val id: EntityRef,
        val visitsCount: Long
    )

    class VisitActionDto(
        val workspace: String
    )

    private data class ActionToProc(
        val workspace: String,
        val user: String,
        val time: Long
    )
}
