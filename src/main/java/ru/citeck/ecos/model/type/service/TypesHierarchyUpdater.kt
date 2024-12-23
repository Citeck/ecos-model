package ru.citeck.ecos.model.type.service

import io.github.oshai.kotlinlogging.KotlinLogging
import ru.citeck.ecos.model.type.service.resolver.AspectsProvider
import ru.citeck.ecos.model.type.service.resolver.TypeDefResolver
import ru.citeck.ecos.model.type.service.resolver.TypesProvider
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.webapp.api.EcosWebAppApi
import ru.citeck.ecos.webapp.api.promise.Promise
import ru.citeck.ecos.webapp.api.promise.Promises
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import ru.citeck.ecos.webapp.lib.registry.MutableEcosRegistry
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
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
    private val typesToUpdateQueue = ArrayBlockingQueue<TypesToUpdate>(100)
    private var lastModifiedTypeCheckedAt: Long = System.currentTimeMillis()

    private val shutdownHook = thread(start = false) { updaterEnabled.set(false) }

    fun start(): TypesHierarchyUpdater {
        updaterEnabled.set(true)
        thread(name = UPDATER_THREAD_NAME, start = true) {
            while (updaterEnabled.get()) {
                if (!checkLastModifiedType()) {
                    update(typesToUpdateQueue.poll(1, TimeUnit.SECONDS))
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

    fun addTypesToUpdate(typesToUpdate: Set<String>): Promise<Unit> {
        if (!updaterEnabled.get()) {
            return Promises.resolve(Unit)
        }
        log.info { "Add types to update: $typesToUpdate" }
        val future = CompletableFuture<Unit>()
        val typesToUpdateWithFuture = TypesToUpdate(typesToUpdate, future)
        if (!webAppApi.isReady()) {
            update(typesToUpdateWithFuture)
        } else {
            typesToUpdateQueue.add(typesToUpdateWithFuture)
        }
        return Promises.create(future)
    }

    private fun update(nextTypesToUpdate: TypesToUpdate?) {

        nextTypesToUpdate ?: return

        val typesIdsToUpdate = LinkedHashSet<String>(nextTypesToUpdate.types)
        val futures = ArrayList<CompletableFuture<Unit>>()
        futures.add(nextTypesToUpdate.future)

        try {
            while (typesToUpdateQueue.isNotEmpty()) {
                typesToUpdateQueue.poll(1, TimeUnit.SECONDS)?.let {
                    typesIdsToUpdate.addAll(it.types)
                    futures.add(it.future)
                } ?: break
            }
            val resolvedTypes = resolver.getResolvedTypesWithMeta(
                typesService.getAllWithMeta(typesIdsToUpdate),
                rawProv,
                resProv,
                aspectsProv
            )
            for (type in resolvedTypes) {
                registry.setValue(type.entity.id, type)
            }
            futures.forEach {
                try {
                    it.complete(Unit)
                } catch (e: Throwable) {
                    log.error(e) { "Exception while future completion. Types: $typesIdsToUpdate" }
                }
            }
        } catch (e: Throwable) {
            if (!updaterEnabled.get()) {
                return
            }
            log.error(e) { "Exception while types resolving" }
            val future = if (futures.size == 1) {
                futures.first()
            } else {
                CompletableFuture<Unit>().thenApply {
                    futures.forEach { it.complete(Unit) }
                }.exceptionally { exception ->
                    futures.forEach { it.completeExceptionally(exception) }
                }
            }
            typesToUpdateQueue.add(TypesToUpdate(typesIdsToUpdate, future))
            if (Thread.currentThread().name == UPDATER_THREAD_NAME) {
                Thread.sleep(10_000)
            }
        }
    }

    fun dispose() {
        updaterEnabled.set(false)
        Runtime.getRuntime().removeShutdownHook(shutdownHook)
    }

    private class TypesToUpdate(
        val types: Set<String>,
        val future: CompletableFuture<Unit>
    )
}
