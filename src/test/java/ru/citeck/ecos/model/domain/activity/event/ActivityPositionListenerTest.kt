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
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts
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
    fun `irina scenario - moving a top-level record under a parent does not collide later top-level positions`() {
        // Reproduces COREDEV-196 item 25: create stages and tasks via the
        // journal, move a top-level task under a stage, then create another
        // top-level task — its position must not collide with existing
        // top-level stages.
        val stage1 = createActivity()
        val stage2 = createActivity()
        val task1 = createActivity(parent = stage1)
        val stage3 = createActivity()
        val task2 = createActivity()

        AuthContext.runAsSystem {
            recordsService.mutateAtt(task2, ATT_PARENT, stage1)
        }

        val task3 = createActivity()

        val topLevelPositions = listOf(
            getPosition(stage1),
            getPosition(stage2),
            getPosition(stage3),
            getPosition(task3)
        )
        val stage1ChildrenPositions = listOf(getPosition(task1), getPosition(task2))

        assertThat(topLevelPositions)
            .`as`("Top-level positions must be unique after a sibling moves under a parent")
            .doesNotHaveDuplicates()
        assertThat(stage1ChildrenPositions)
            .`as`("Children of stage1 must have unique positions")
            .doesNotHaveDuplicates()
        assertThat(getPosition(task3))
            .`as`("task3 must land at the end of the top-level list, not in the middle")
            .isGreaterThan(getPosition(stage3))
    }

    @Test
    fun `creating a sibling does not collide with existing positions when there are gaps`() {
        // Real-world position lists rarely stay dense: records get deleted,
        // bulk-imported with custom positions, or fixed up by hand. COUNT+1
        // collides as soon as `count(remaining) + 1` lands on an existing
        // position. Reproduce that with deletion-induced gap.
        val parent = createActivity()
        val first = createActivity(parent = parent)
        val second = createActivity(parent = parent)
        val third = createActivity(parent = parent)
        assertThat(getPosition(first)).isEqualTo(1)
        assertThat(getPosition(second)).isEqualTo(2)
        assertThat(getPosition(third)).isEqualTo(3)

        AuthContext.runAsSystem { recordsService.delete(first) }
        refsToDelete.remove(first)

        val fourth = createActivity(parent = parent)

        val remaining = listOf(getPosition(second), getPosition(third), getPosition(fourth))
        assertThat(remaining)
            .`as`("Positions of remaining siblings must stay unique")
            .doesNotHaveDuplicates()
        assertThat(getPosition(fourth))
            .`as`("A freshly created sibling must land past the highest existing position")
            .isGreaterThan(getPosition(third))
    }

    @Test
    fun `moving a record with an explicit new position preserves it instead of recomputing`() {
        // Drag-and-drop on the chart can send `parent` and `position` together
        // when the user drops a task into the middle of another parent's list.
        // The user's intent (the chosen slot) must win — the listener should
        // not overwrite an explicitly supplied position.
        val parentA = createActivity()
        val parentB = createActivity()
        createActivity(parent = parentB)
        createActivity(parent = parentB)
        createActivity(parent = parentB)
        val mover = createActivity(parent = parentA)

        AuthContext.runAsSystem {
            recordsService.mutate(
                RecordAtts(mover).apply {
                    setAtt(ATT_PARENT, parentB)
                    setAtt(ATT_POSITION, 2)
                }
            )
        }

        assertThat(getPosition(mover))
            .`as`("Explicit position from the same mutate must be honored")
            .isEqualTo(2)
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
