package ru.citeck.ecos.model.domain.activity.event

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.data.sql.domain.DbDomainConfig
import ru.citeck.ecos.data.sql.domain.DbDomainFactory
import ru.citeck.ecos.data.sql.records.DbRecordsDaoConfig
import ru.citeck.ecos.data.sql.service.DbDataServiceConfig
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosModelApp::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ActivityPositionListenerTest {

    companion object {
        private const val TEST_SOURCE_ID = "activity-position-test"
        private const val TEST_TYPE_ID = "activity-position-test"
        private const val ATT_PARENT = "activity:parent"
        private const val ATT_POSITION = "activity:position"
    }

    @Autowired
    private lateinit var recordsService: RecordsService

    @Autowired
    private lateinit var dbDomainFactory: DbDomainFactory

    private val refsToDelete = mutableListOf<EntityRef>()

    @BeforeAll
    fun setUp() {
        AuthContext.runAsSystem {
            val dao = dbDomainFactory.create(
                DbDomainConfig.create()
                    .withRecordsDao(
                        DbRecordsDaoConfig.create {
                            withId(TEST_SOURCE_ID)
                            withTypeRef(ModelUtils.getTypeRef(TEST_TYPE_ID))
                        }
                    )
                    .withDataService(
                        DbDataServiceConfig.create {
                            withTable("test_activity_position")
                            withStoreTableMeta(true)
                        }
                    ).build()
            ).withSchema("ecos_data").build()
            recordsService.register(dao)

            recordsService.create(
                "emodel/types-repo",
                ObjectData.create()
                    .set("id", TEST_TYPE_ID)
                    .set(
                        "aspects",
                        listOf(mapOf("ref" to "emodel/aspect@activity-atts"))
                    )
                    .set(
                        "model",
                        TypeModelDef.create {
                            withAttributes(
                                listOf(
                                    AttributeDef.create { withId("name") }
                                )
                            )
                        }
                    )
            )
        }
    }

    @AfterAll
    fun tearDown() {
        AuthContext.runAsSystem {
            for (ref in refsToDelete.reversed()) {
                try {
                    recordsService.delete(ref)
                } catch (_: Exception) {
                }
            }
        }
        recordsService.unregister(TEST_SOURCE_ID)
    }

    @Test
    fun `siblings under the same parent get sequential 1,2,3 positions on create`() {
        val parent = createActivity()

        val first = createActivity(parent = parent)
        val second = createActivity(parent = parent)
        val third = createActivity(parent = parent)

        assertThat(getPosition(first)).isEqualTo(1)
        assertThat(getPosition(second)).isEqualTo(2)
        assertThat(getPosition(third)).isEqualTo(3)
    }

    @Test
    fun `top-level (no parent) records are numbered per-source 1,2,3`() {
        val a = createActivity()
        val b = createActivity()
        val c = createActivity()

        // The test source starts empty for this run; sequential numbering
        // continues from however many records the prior tests created.
        // We assert STRICT monotonic progression, not absolute values.
        val pa = getPosition(a)
        val pb = getPosition(b)
        val pc = getPosition(c)
        assertThat(pb).isEqualTo(pa + 1)
        assertThat(pc).isEqualTo(pb + 1)
    }

    @Test
    fun `moving a record under a new parent re-assigns position based on the new sibling count`() {
        val parentA = createActivity()
        val parentB = createActivity()

        createActivity(parent = parentA)
        createActivity(parent = parentA)

        val mover = createActivity(parent = parentA)
        assertThat(getPosition(mover)).isEqualTo(3)

        // pre-populate parentB so the re-assignment lands at 2, not 1
        createActivity(parent = parentB)

        AuthContext.runAsSystem {
            recordsService.mutateAtt(mover, ATT_PARENT, parentB)
        }

        assertThat(getPosition(mover)).isEqualTo(2)
    }

    @Test
    fun `user-supplied position is overwritten on create`() {
        val parent = createActivity()
        createActivity(parent = parent)

        val withExplicitPosition = createActivity(parent = parent, explicitPosition = 999)

        // Listener fires after the explicit value is persisted, recounts
        // siblings (1 — the first child) and forces position to 2.
        assertThat(getPosition(withExplicitPosition)).isEqualTo(2)
    }

    private fun createActivity(parent: EntityRef? = null, explicitPosition: Int? = null): EntityRef {
        return AuthContext.runAsSystem {
            val atts = ObjectData.create()
                .set(RecordConstants.ATT_TYPE, "emodel/type@$TEST_TYPE_ID")
                .set("activity:title", "test-${System.nanoTime()}")
            if (parent != null && parent != EntityRef.EMPTY) {
                atts.set(ATT_PARENT, parent)
            }
            if (explicitPosition != null) {
                atts.set(ATT_POSITION, explicitPosition)
            }
            val ref = recordsService.create(TEST_SOURCE_ID, atts)
            refsToDelete.add(ref)
            ref
        }
    }

    private fun getPosition(ref: EntityRef): Int {
        return AuthContext.runAsSystem {
            recordsService.getAtt(ref, "$ATT_POSITION?num").asInt()
        }
    }
}
