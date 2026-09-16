package ru.citeck.ecos.model.domain.authsync

import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.model.lib.authorities.sync.AuthoritiesSync
import ru.citeck.ecos.model.lib.authorities.sync.AuthoritiesSyncContext
import ru.citeck.ecos.model.lib.authorities.sync.AuthoritiesSyncFactory
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import java.util.concurrent.CopyOnWriteArrayList

data class TestSyncConfig(val marker: String = "")
data class TestSyncState(val marker: String = "")

/**
 * Fake [AuthoritiesSyncFactory] used only in tests to drive [ru.citeck.ecos.model.domain.authsync.service.AuthoritiesSyncService]
 * without a real LDAP/Alfresco backend. Test controls what [AuthoritiesSyncContext.updateAuthorities] batches
 * are emitted by [execute] and can simulate a mid-sync failure via [failAfterBatchIndex].
 */
@Component
class TestAuthoritiesSyncFactory : AuthoritiesSyncFactory<TestSyncConfig, TestSyncState> {

    companion object {
        const val TYPE = "test-fake-sync"
    }

    @Volatile
    var batches: List<List<ObjectData>> = emptyList()

    @Volatile
    var failAfterBatchIndex: Int? = null

    val executedBatches = CopyOnWriteArrayList<Int>()

    fun reset() {
        batches = emptyList()
        failAfterBatchIndex = null
        executedBatches.clear()
    }

    override fun createSync(
        id: String,
        config: TestSyncConfig,
        authorityType: AuthorityType,
        context: AuthoritiesSyncContext<TestSyncState>
    ): AuthoritiesSync<TestSyncState> {
        return TestSync(context)
    }

    override fun getType(): String = TYPE

    inner class TestSync(
        private val context: AuthoritiesSyncContext<TestSyncState>
    ) : AuthoritiesSync<TestSyncState> {

        override fun execute(state: TestSyncState?): Boolean {
            batches.forEachIndexed { idx, batch ->
                context.updateAuthorities(AuthorityType.PERSON, batch)
                executedBatches.add(idx)
                if (failAfterBatchIndex == idx) {
                    error("Simulated failure after batch $idx")
                }
            }
            return true
        }

        override fun getManagedAtts(): Set<String> = emptySet()

        override fun mutate(record: LocalRecordAtts, newRecord: Boolean): String {
            // Unlike the real LDAP sync, this fake has no read-only backend, so it accepts
            // mutations (including the delete-driven ones from test cleanup) instead of
            // rejecting them.
            return record.id
        }
    }
}
