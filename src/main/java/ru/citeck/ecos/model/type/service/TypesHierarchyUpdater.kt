package ru.citeck.ecos.model.type.service

import io.github.oshai.kotlinlogging.KotlinLogging
import ru.citeck.ecos.model.type.service.resolver.AspectsProvider
import ru.citeck.ecos.model.type.service.resolver.TypeDefResolver
import ru.citeck.ecos.model.type.service.resolver.TypesProvider
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.txn.lib.TxnContext
import ru.citeck.ecos.webapp.api.EcosWebAppApi
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import ru.citeck.ecos.webapp.lib.registry.MutableEcosRegistry
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class TypesHierarchyUpdater(
    private val typesService: TypesService,
    private val resolver: TypeDefResolver,
    private val rawProv: TypesProvider,
    private val resProv: TypesProvider,
    private val aspectsProv: AspectsProvider,
    private val registry: MutableEcosRegistry<TypeDef>,
    private val webAppApi: EcosWebAppApi,
    private val syncAllTypes: () -> Unit
) {

    companion object {
        private val log = KotlinLogging.logger {}

        private val LAST_MODIFIED_TYPE_CHECK_PERIOD: Long = Duration.ofMinutes(10).toMillis()

        private const val UPDATER_THREAD_NAME = "types-hierarchy-updater"
    }
    private val updaterEnabled = AtomicBoolean()
    private val typesToUpdateQueue = ArrayBlockingQueue<UpdateCommand>(100)
    private var lastModifiedTypeCheckedAt: Long = System.currentTimeMillis()

    private val shutdownHook = thread(start = false) { updaterEnabled.set(false) }

    fun start(): TypesHierarchyUpdater {
        updaterEnabled.set(true)
        thread(name = UPDATER_THREAD_NAME, start = true) {
            while (updaterEnabled.get()) {
                if (!checkLastModifiedType()) {
                    update(typesToUpdateQueue.poll(1, TimeUnit.SECONDS), Duration.ofHours(1))
                }
            }
        }
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        return this
    }

    private fun checkLastModifiedType(): Boolean {
        if (System.currentTimeMillis() - lastModifiedTypeCheckedAt < LAST_MODIFIED_TYPE_CHECK_PERIOD) {
            return false
        }
        lastModifiedTypeCheckedAt = System.currentTimeMillis()
        log.debug { "Last modified type checking started" }
        val lastModifiedType = typesService.getAllWithMeta(
            1,
            0,
            Predicates.le(
                RecordConstants.ATT_MODIFIED,
                Instant.now().minus(1, ChronoUnit.MINUTES)
            ),
            listOf(SortBy(RecordConstants.ATT_MODIFIED, ascending = false))
        ).firstOrNull()

        if (lastModifiedType == null) {
            log.debug { "Last modified type is null" }
            return false
        } else {
            log.debug {
                "Last modified type is '${lastModifiedType.entity.id}' " +
                    "with modified time: ${lastModifiedType.meta.modified}"
            }
        }
        val lastModifiedFromRegistry = registry.getValueWithMeta(lastModifiedType.entity.id)?.meta?.modified
        val lastModifiedFromRepo = lastModifiedType.meta.modified
        return if (lastModifiedFromRegistry != lastModifiedFromRepo) {
            log.info {
                "Found unmatched modified time for '${lastModifiedType.entity.id}'. " +
                    "Registry time: $lastModifiedFromRegistry Repo time: $lastModifiedFromRepo"
            }
            syncAllTypes()
            true
        } else {
            false
        }
    }

    fun updateTypes(types: Set<String>) {
        if (!updaterEnabled.get()) {
            return
        }
        log.info { "Add types to update: $types" }
        val typesToUpdate = TypesToUpdate(types)
        try {
            val timeout = if (!webAppApi.isReady()) {
                Duration.ofMinutes(5)
            } else {
                Duration.ofSeconds(20)
            }
            TxnContext.doAfterRollback(0f, false) {
                try {
                    update(typesToUpdate, Duration.ofSeconds(20))
                } catch (e: TimeoutException) {
                    typesToUpdateQueue.add(typesToUpdate)
                }
            }
            update(typesToUpdate, timeout)
        } catch (e: TimeoutException) {
            log.warn {
                "Updating the types definition takes too much time. " +
                "Calculation will be done later. Types: $types"
            }
            TxnContext.doAfterCommit(0f, false) {
                typesToUpdateQueue.add(typesToUpdate)
            }
        }
    }

    private fun fillTypesToUpdate(command: UpdateCommand, types: MutableSet<String>) {
        when (command) {
            is UpdateAll -> typesService.getAll().forEach { types.add(it.id) }
            is TypesToUpdate -> command.types.forEach { types.add(it) }
        }
    }

    private fun update(updateCommand: UpdateCommand?, timeout: Duration) {

        updateCommand ?: return

        val isUpdaterThread = Thread.currentThread().name == UPDATER_THREAD_NAME

        val typesIdsToUpdate = LinkedHashSet<String>()
        fillTypesToUpdate(updateCommand, typesIdsToUpdate)

        try {
            while (typesToUpdateQueue.isNotEmpty()) {
                val cmd = typesToUpdateQueue.poll(1, TimeUnit.SECONDS) ?: break
                fillTypesToUpdate(cmd, typesIdsToUpdate)
                if (cmd is UpdateAll) {
                    typesToUpdateQueue.clear()
                    break
                }
            }
            val resolvedTypes = resolver.getResolvedTypesWithMeta(
                typesService.getAllWithMeta(typesIdsToUpdate),
                rawProv,
                resProv,
                aspectsProv,
                timeout
            )
            for (type in resolvedTypes) {
                registry.setValue(type.entity.id, type)
            }
        } catch (e: Throwable) {
            if (!isUpdaterThread && e is TimeoutException) {
                throw e
            }
            if (isUpdaterThread && !updaterEnabled.get()) {
                return
            }
            log.error(e) { "Exception while types resolving. Command: $updateCommand" }
            typesToUpdateQueue.add(TypesToUpdate(typesIdsToUpdate))
            if (isUpdaterThread) {
                Thread.sleep(10_000)
            }
        }
    }

    fun dispose() {
        updaterEnabled.set(false)
        Runtime.getRuntime().removeShutdownHook(shutdownHook)
    }

    private sealed class UpdateCommand

    private data class TypesToUpdate(
        val types: Set<String>
    ) : UpdateCommand()

    private data object UpdateAll : UpdateCommand()
}
