package ru.citeck.ecos.model.type.service

import mu.KotlinLogging
import ru.citeck.ecos.commons.promise.Promises
import ru.citeck.ecos.model.type.service.resolver.AspectsProvider
import ru.citeck.ecos.model.type.service.resolver.TypeDefResolver
import ru.citeck.ecos.model.type.service.resolver.TypesProvider
import ru.citeck.ecos.webapp.api.promise.Promise
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import ru.citeck.ecos.webapp.lib.registry.MutableEcosRegistry
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
    private val registry: MutableEcosRegistry<TypeDef>
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }
    private val updaterEnabled = AtomicBoolean()
    private val typesToUpdateQueue = ArrayBlockingQueue<TypesToUpdate>(100)

    private val shutdownHook = thread(start = false) { updaterEnabled.set(false) }

    fun start(): TypesHierarchyUpdater {
        updaterEnabled.set(true)
        thread(start = true) {
            while (updaterEnabled.get()) {
                update(typesToUpdateQueue.poll(1, TimeUnit.SECONDS))
            }
        }
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        return this
    }

    fun addTypesToUpdate(typesToUpdate: Set<String>): Promise<Unit> {
        if (!updaterEnabled.get()) {
            return Promises.resolve(Unit)
        }
        log.info { "Add types to update: $typesToUpdate" }
        val future = CompletableFuture<Unit>()
        typesToUpdateQueue.add(TypesToUpdate(typesToUpdate, future))
        return Promises.create(future)
    }

    private fun update(nextTypesToUpdate: TypesToUpdate?) {

        nextTypesToUpdate ?: return

        val typesIdsToUpdate = LinkedHashSet<String>(nextTypesToUpdate.types)
        val futures = ArrayList<CompletableFuture<Unit>>()
        futures.add(nextTypesToUpdate.future)

        try {
            while (updaterEnabled.get() && typesToUpdateQueue.isNotEmpty()) {
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
                if (updaterEnabled.get()) {
                    registry.setValue(type.entity.id, type)
                } else {
                    break
                }
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
            Thread.sleep(10_000)
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
