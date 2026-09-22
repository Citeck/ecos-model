package ru.citeck.ecos.model.domain.authsync

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.model.domain.authorities.AuthoritiesTestBase
import ru.citeck.ecos.model.domain.authsync.service.AuthoritiesSyncService
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.webapp.api.entity.EntityRef

/**
 * Covers [AuthoritiesSyncService] using a fake [TestAuthoritiesSyncFactory] instead of a real
 * LDAP/Alfresco backend. In particular, verifies that authorities produced by earlier sync batches
 * are actually persisted rather than rolled back together with the whole sync when a later batch
 * fails - the property that broke when the whole scheduled sync run was wrapped in a single
 * transaction (see AuthoritiesSyncService.updateSynchronizations()).
 */
class AuthoritiesSyncServiceTest : AuthoritiesTestBase() {

    @Autowired
    lateinit var syncService: AuthoritiesSyncService

    @Autowired
    lateinit var testFactory: TestAuthoritiesSyncFactory

    private val createdSyncRefs = mutableListOf<EntityRef>()

    @BeforeEach
    fun resetFactory() {
        testFactory.reset()
    }

    @AfterEach
    fun cleanupSyncDefs() {
        AuthContext.runAsSystem {
            createdSyncRefs.forEach {
                if (!recordsService.getAtt(it, RecordConstants.ATT_NOT_EXISTS).asBoolean()) {
                    recordsService.delete(it)
                }
            }
        }
        createdSyncRefs.clear()
        AuthContext.runAsSystem { syncService.updateSynchronizations() }
    }

    private fun createSyncDef(id: String, priority: Int = 0): EntityRef {
        val atts = ObjectData.create()
        atts["id"] = id
        atts["name"] = "Test sync $id"
        atts["type"] = TestAuthoritiesSyncFactory.TYPE
        atts["enabled"] = true
        atts["priority"] = priority
        atts["authorityType"] = AuthorityType.PERSON
        atts["manageNewAuthorities"] = false
        atts["repeatDelayDuration"] = ""
        atts["version"] = 0
        atts["config"] = ObjectData.create()
        val ref = AuthContext.runAsSystem {
            recordsService.create(AuthoritiesSyncService.SOURCE_ID, atts)
        }
        createdSyncRefs.add(ref)
        return ref
    }

    private fun runSync(id: String) {
        AuthContext.runAsSystem {
            syncService.updateSynchronizations()
            syncService.runById(id)
        }
    }

    private fun personAtts(id: String, firstName: String): ObjectData {
        return ObjectData.create().set("id", id).set("firstName", firstName)
    }

    private fun personExists(id: String): Boolean {
        return !recordsService.getAtt(AuthorityType.PERSON.getRef(id), RecordConstants.ATT_NOT_EXISTS).asBoolean()
    }

    @Test
    fun syncPersistsAuthoritiesProducedByFactory() {
        testFactory.batches = listOf(listOf(personAtts("sync-person-1", "First Sync Name")))
        val ref = createSyncDef("test-sync-create")

        runSync(ref.getLocalId())

        assertThat(personExists("sync-person-1")).isTrue()
        val firstName = recordsService.getAtt(AuthorityType.PERSON.getRef("sync-person-1"), "firstName").asText()
        assertThat(firstName).isEqualTo("First Sync Name")
    }

    @Test
    fun higherPrioritySyncTakesOverManagementFromLowerPrioritySync() {
        testFactory.batches = listOf(listOf(personAtts("sync-person-2", "From low priority")))
        val lowPrioritySync = createSyncDef("test-sync-low", priority = 0)
        runSync(lowPrioritySync.getLocalId())

        testFactory.reset()
        testFactory.batches = listOf(listOf(personAtts("sync-person-2", "From same priority")))
        val samePrioritySync = createSyncDef("test-sync-same", priority = 0)
        runSync(samePrioritySync.getLocalId())

        var firstName = recordsService.getAtt(AuthorityType.PERSON.getRef("sync-person-2"), "firstName").asText()
        assertThat(firstName)
            .describedAs("a sync with equal priority must not steal management from the current owner")
            .isEqualTo("From low priority")

        testFactory.reset()
        testFactory.batches = listOf(listOf(personAtts("sync-person-2", "From high priority")))
        val highPrioritySync = createSyncDef("test-sync-high", priority = 10)
        runSync(highPrioritySync.getLocalId())

        firstName = recordsService.getAtt(AuthorityType.PERSON.getRef("sync-person-2"), "firstName").asText()
        assertThat(firstName).isEqualTo("From high priority")
    }

    @Test
    fun authoritiesFromEarlierBatchesAreKeptWhenLaterBatchFails() {
        testFactory.batches = listOf(
            listOf(personAtts("sync-person-3", "Batch One")),
            listOf(personAtts("sync-person-4", "Batch Two"))
        )
        testFactory.failAfterBatchIndex = 0
        val ref = createSyncDef("test-sync-fail")

        AuthContext.runAsSystem { syncService.updateSynchronizations() }
        assertThrows<Exception> {
            AuthContext.runAsSystem { syncService.runById(ref.getLocalId()) }
        }

        assertThat(personExists("sync-person-3"))
            .describedAs("authority from the batch processed before the failure must be persisted, not rolled back")
            .isTrue()
        assertThat(personExists("sync-person-4")).isFalse()
    }
}
