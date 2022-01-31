package ru.citeck.ecos.model.domain.authsync.service

import mu.KotlinLogging
import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.core.LockProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.commons.utils.ReflectUtils
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.predicate.model.VoidPredicate
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture

@Service
class AuthoritiesSyncService(
    private val recordsService: RecordsService,
    private val taskScheduler: TaskScheduler,
    private val lockProvider: LockProvider
) {

    companion object {
        const val SOURCE_ID = "authorities-sync"
        val TYPE_REF = TypeUtils.getTypeRef(SOURCE_ID)

        private val log = KotlinLogging.logger {}
    }

    private val syncFactories = ConcurrentHashMap<String, AuthoritiesSyncFactory<Any, Any>>()
    private val syncInstances = ConcurrentHashMap<String, SyncInstance>()
    private val scheduledTasks = ConcurrentHashMap<String, ScheduledFuture<*>>()
    private var newAuthoritiesManagers: Map<AuthorityType, String> = emptyMap()

    private val syncContext = ThreadLocal<Boolean>()

    fun isSyncContext(): Boolean {
        return syncContext.get() == true
    }

    fun isSyncEnabled(syncId: String?): Boolean {
        syncId ?: return false
        return syncInstances[syncId]?.config?.enabled == true
    }

    fun isNewAuthoritiesManaged(authorityType: AuthorityType): Boolean {
        return newAuthoritiesManagers.containsKey(authorityType)
    }

    fun getManagedAtts(syncId: String?): Set<String> {
        syncId ?: return emptySet()
        return syncInstances[syncId]?.sync?.getManagedAtts() ?: emptySet()
    }

    fun create(authorityType: AuthorityType, record: LocalRecordAtts): String {

        val newAuthorityManagerId = newAuthoritiesManagers[authorityType]
            ?: error("New authorities manager is not found for type $authorityType")
        val syncInstance = syncInstances[newAuthorityManagerId]
            ?: error("Sync instance is not found by id $newAuthorityManagerId")

        val attributes = record.attributes.deepCopy()
        attributes.set("managedBySync", RecordRef.create("emodel", SOURCE_ID, syncInstance.id))

        return syncInstance.sync.mutate(LocalRecordAtts(record.id, attributes), true)
    }

    fun update(syncId: String, record: LocalRecordAtts): String {
        val sync = syncInstances[syncId] ?: error("Sync instance is not found for id $syncId")
        return sync.sync.mutate(record, false)
    }

    @Synchronized
    fun updateSynchronizations() {

        log.info { "Update synchronizations started" }

        val allSyncs = recordsService.query(RecordsQuery.create {
            withSourceId(SOURCE_ID)
            withQuery(VoidPredicate.INSTANCE)
        }, AuthoritiesSyncDef::class.java)

        log.info { "Found ${allSyncs.getRecords().size} synchronizations" }

        var removedSyncCount = 0
        var registeredSyncCount = 0

        val records = allSyncs.getRecords().filter {
            it.id.isNotBlank()
        }.associateBy {
            it.id
        }
        syncInstances.keys.filter {
            !records.containsKey(it)
        }.forEach {
            log.info { "Remove $it synchronization" }
            syncInstances.remove(it)?.sync?.stop()
            scheduledTasks[it]?.cancel(false)
            removedSyncCount++
        }

        for ((syncId, v) in records) {

            val syncInstanceConfig = SyncInstanceConfig(
                v.config,
                v.enabled,
                v.version,
                v.authorityType,
                v.manageNewAuthorities,
                v.repeatDelayDuration
            )

            val current = syncInstances[syncId]

            if (current == null || current.config != syncInstanceConfig) {

                val factory = syncFactories[v.type]

                if (factory == null) {

                    log.error { "Factory with type ${v.type} is not found" }
                    current?.sync?.stop()
                    syncInstances.remove(syncId)
                    scheduledTasks.remove(syncId)?.cancel(false)

                } else {

                    val configAndStateTypes = ReflectUtils.getGenericArgs(
                        factory::class.java,
                        AuthoritiesSyncFactory::class.java
                    )
                    if (configAndStateTypes.size != 2) {
                        error("Invalid factory: $factory")
                    }
                    val convertedConfig = v.config.getAs(configAndStateTypes[0])
                        ?: error("Config can't be converted to ${configAndStateTypes[0]}")

                    log.info { "Register new synchronization instance: $syncId" }
                    registeredSyncCount++

                    val newInstance = SyncInstance(
                        syncId,
                        factory.createSync(convertedConfig, v.authorityType, createContext(syncId)),
                        configAndStateTypes[1],
                        syncInstanceConfig
                    )
                    current?.sync?.stop()
                    syncInstances[syncId] = newInstance
                    scheduledTasks.remove(syncId)?.cancel(false)

                    if (v.enabled && v.repeatDelayDuration.isNotBlank()) {
                        log.info { "Start synchronization '$syncId' with period ${v.repeatDelayDuration} " }
                        val repeatDelayDuration = try {
                            Duration.parse(v.repeatDelayDuration)
                        } catch (e: DateTimeParseException) {
                            log.error { "Incorrect duration string: '${v.repeatDelayDuration}' in sync $syncId" }
                            null
                        }
                        if (repeatDelayDuration != null) {
                            scheduledTasks[syncId] = taskScheduler.scheduleWithFixedDelay(
                                {
                                    AuthContext.runAsSystem {
                                        run(newInstance, true)
                                    }
                                },
                                repeatDelayDuration
                            )
                        }
                    }
                    if (v.enabled) {
                        newInstance.sync.start()
                    }
                }
            }
        }

        val newAuthoritiesManagers = mutableMapOf<AuthorityType, String>()
        syncInstances.forEach { (k, v) ->
            if (v.config.enabled && v.config.manageNewAuthorities) {
                val currentManagerId = newAuthoritiesManagers[v.config.authorityType]
                if (currentManagerId != null) {
                    log.warn {
                        "Found two synchronizations which manage one authority " +
                        "type ${v.config.authorityType}. Current: $currentManagerId New: ${v.id}"
                    }
                } else {
                    newAuthoritiesManagers[v.config.authorityType] = k
                }
            }
        }
        this.newAuthoritiesManagers = newAuthoritiesManagers

        log.info { "Synchronizations initialization completed. " +
            "Removed: $removedSyncCount Registered: $registeredSyncCount" }
    }

    fun runById(id: String) {
        run(syncInstances[id] ?: error("Synchronization is not found: $id"), false)
    }

    private fun run(sync: SyncInstance, runByJob: Boolean) {
        synchronized(sync) {
            val lockConfig = LockConfiguration(
                "ecos-authorities-sync-" + sync.id,
                Instant.now().plus(Duration.ofMinutes(1))
            )
            var lock = lockProvider.lock(lockConfig)
            if (!lock.isPresent) {
                if (runByJob) {
                    log.debug { "Authorities sync ${sync.id} is performed by another app instance. Skip it" }
                    return
                }
                for (i in 0..3) {
                    Thread.sleep(1000)
                    lock = lockProvider.lock(lockConfig)
                    if (lock.isPresent) {
                        break
                    }
                }
                if (!lock.isPresent) {
                    error("Authorities sync is locked and can't be started")
                }
            }
            try {
                runInSync(sync)
            } finally {
                try {
                    lock.get().unlock()
                } catch (e: Exception) {
                    log.warn(e) { "Exception while lock releasing for syn ${sync.id}" }
                }
            }
        }
    }

    private fun runInSync(sync: SyncInstance) {

        log.debug { "==== Run synchronization ${sync.id} ====" }

        val ref = RecordRef.create("emodel", SOURCE_ID, sync.id)

        val stateData = recordsService.getAtts(ref, SyncStateAtts::class.java)
        val state = if (stateData.stateVersion < sync.config.version) {
            recordsService.mutate(ref, mapOf(
                "state" to ObjectData.create(),
                "stateVersion" to sync.config.version
            ))
            null
        } else if (stateData.state != null && stateData.state.size() > 0) {
            stateData.state
        } else {
            null
        }
        val convertedState = Json.mapper.convert(state, sync.stateType)

        sync.sync.execute(convertedState)

        recordsService.mutateAtt(ref, "lastSync", Instant.now())
    }

    private fun createContext(syncId: String): AuthoritiesSyncContext<Any> {

        val ref = RecordRef.create("emodel", SOURCE_ID, syncId)

        return object : AuthoritiesSyncContext<Any> {
            override fun setState(state: Any?) {
                recordsService.mutateAtt(ref, "state", state)
            }
            override fun updateAuthorities(type: AuthorityType, authorities: List<ObjectData>) {
                for (authorityAtts in authorities) {
                    val idValue = authorityAtts.get("id").asText()
                    if (idValue.isBlank()) {
                        error("Empty id")
                    }
                    val userRef = RecordRef.create(type.sourceId, idValue)
                    val currentAuthorityAtts = recordsService.getAtts(userRef, CurrentAuthorityAtts::class.java)

                    val attsCopy = authorityAtts.deepCopy()
                    syncContext.set(true)
                    try {
                        val isEmptyManagedBySync = RecordRef.isEmpty(currentAuthorityAtts.managedBySync)
                        if (currentAuthorityAtts.notExists == true || isEmptyManagedBySync) {
                            attsCopy.set("managedBySync", ref)
                        }
                        if (currentAuthorityAtts.notExists == true) {
                            recordsService.create(type.sourceId, attsCopy)
                        } else if (isEmptyManagedBySync || currentAuthorityAtts.managedBySync == ref) {
                            recordsService.mutate(RecordAtts(RecordRef.create(type.sourceId, ""), attsCopy))
                        }
                    } finally {
                        syncContext.remove()
                    }
                }
            }
        }
    }

    @Autowired
    fun setSyncFactories(factories: List<AuthoritiesSyncFactory<*, *>>) {
        factories.forEach {
            @Suppress("UNCHECKED_CAST")
            val factory = it as AuthoritiesSyncFactory<Any, Any>
            syncFactories[it.getType()] = factory
        }
    }

    @EventListener
    fun onServicesInitialized(event: ContextRefreshedEvent) {
        updateSynchronizations()
    }

    private class SyncInstance(
        val id: String,
        val sync: AuthoritiesSync<Any>,
        val stateType: Class<*>,
        val config: SyncInstanceConfig
    )

    private data class SyncInstanceConfig(
        val config: ObjectData,
        val enabled: Boolean,
        val version: Int,
        val authorityType: AuthorityType,
        val manageNewAuthorities: Boolean,
        val repeatDelayDuration: String
    )

    private class CurrentAuthorityAtts(
        @AttName("_notExists")
        val notExists: Boolean? = null,
        val managedBySync: RecordRef? = null
    )

    private class SyncStateAtts(
        val state: ObjectData?,
        val stateVersion: Int = 0
    )
}
