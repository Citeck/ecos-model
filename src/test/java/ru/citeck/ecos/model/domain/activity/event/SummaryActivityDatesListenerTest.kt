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
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.time.Instant
import java.time.temporal.ChronoUnit

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosModelApp::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SummaryActivityDatesListenerTest {

    companion object {
        private const val TEST_SOURCE_ID = "activity-dates-test"
        private const val TEST_TYPE_ID = "activity-dates-test"
        private const val ATT_PARENT = "activity:parent"
        private const val ATT_START = "activity:start"
        private const val ATT_BASE_END = "activity:baseEnd"
        private const val ATT_TYPE = "activity:type"
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
                            withTable("test_activity_dates")
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
    fun `summary with no children falls back to _created and _created + 1 day`() {
        val summary = createSummary()

        val (start, baseEnd, created) = getDates(summary)

        // baseline window is _created → _created + 1 day rounded to whatever
        // precision the storage preserves.
        assertThat(start).isNotNull
        assertThat(baseEnd).isNotNull
        assertThat(start).isEqualTo(created)
        assertThat(baseEnd).isEqualTo(created!!.plus(1, ChronoUnit.DAYS))
    }

    @Test
    fun `summary adopts first child's start and baseEnd entirely — not min-max with the seed`() {
        // Regression for the "duration via journal" bug: original listener
        // seeded a fresh summary with start=_created / baseEnd=_created+1day
        // and then only expanded via min/max — so adding a task starting
        // well after the summary's creation day left the summary stretched
        // back to that creation day. With the new aggregator the summary
        // mirrors the only child exactly.
        val summary = createSummary()
        val taskStart = instantAtUtcDay(2026, 6, 27)
        val taskEnd = instantAtUtcDay(2026, 6, 28)
        createTask(parent = summary, start = taskStart, baseEnd = taskEnd)

        val (start, baseEnd) = getDates(summary)
        assertThat(start).isEqualTo(taskStart)
        assertThat(baseEnd).isEqualTo(taskEnd)
    }

    @Test
    fun `summary aggregates min start and max baseEnd across multiple children`() {
        val summary = createSummary()
        createTask(
            parent = summary,
            start = instantAtUtcDay(2026, 6, 25),
            baseEnd = instantAtUtcDay(2026, 6, 27)
        )
        createTask(
            parent = summary,
            start = instantAtUtcDay(2026, 6, 28),
            baseEnd = instantAtUtcDay(2026, 6, 30)
        )

        val (start, baseEnd) = getDates(summary)
        assertThat(start).isEqualTo(instantAtUtcDay(2026, 6, 25))
        assertThat(baseEnd).isEqualTo(instantAtUtcDay(2026, 6, 30))
    }

    @Test
    fun `deleting a summary with attached tasks does not crash on flush`() {
        // Regression for the "Record not found" bug: DbRecordsDeleteDao
        // clears every source assoc (each task's activity:parent → summary)
        // by mutating the task before the summary itself is deleted. Each
        // such mutation fires a parentMoved event that, in the old code,
        // re-registered the about-to-be-deleted summary for flush — and
        // the flush then crashed trying to mutate a non-existent record.
        val summary = createSummary()
        createTask(parent = summary, start = instantAtUtcDay(2026, 6, 27))
        createTask(parent = summary, start = instantAtUtcDay(2026, 6, 28))

        AuthContext.runAsSystem {
            recordsService.delete(summary)
        }
        // Reaching this assertion at all means flushPending skipped the
        // deleted summary instead of throwing.
        val typeId = recordsService.getAtt(summary, "_type?localId").asText()
        assertThat(typeId).isEmpty()
    }

    @Test
    fun `removing only child reverts summary to created-based fallback`() {
        val summary = createSummary()
        val task = createTask(
            parent = summary,
            start = instantAtUtcDay(2026, 6, 27),
            baseEnd = instantAtUtcDay(2026, 6, 30)
        )
        // sanity: aggregator picked up the child
        val (afterCreate, _) = getDates(summary)
        assertThat(afterCreate).isEqualTo(instantAtUtcDay(2026, 6, 27))

        AuthContext.runAsSystem {
            recordsService.delete(task)
        }

        val (start, baseEnd, created) = getDates(summary)
        assertThat(start).isEqualTo(created)
        assertThat(baseEnd).isEqualTo(created!!.plus(1, ChronoUnit.DAYS))
    }

    @Test
    fun `nested summary is treated as opaque — outer summary picks up sub-summary aggregates`() {
        val outer = createSummary()
        val inner = createSummary(parent = outer)
        createTask(
            parent = inner,
            start = instantAtUtcDay(2026, 6, 27),
            baseEnd = instantAtUtcDay(2026, 6, 30)
        )

        // sub-summary itself first aggregates from its task
        val (innerStart, innerEnd) = getDates(inner)
        assertThat(innerStart).isEqualTo(instantAtUtcDay(2026, 6, 27))
        assertThat(innerEnd).isEqualTo(instantAtUtcDay(2026, 6, 30))

        // outer summary, treating inner as a leaf, propagates inner's window up
        val (outerStart, outerEnd) = getDates(outer)
        assertThat(outerStart).isEqualTo(instantAtUtcDay(2026, 6, 27))
        assertThat(outerEnd).isEqualTo(instantAtUtcDay(2026, 6, 30))
    }

    private fun createSummary(parent: EntityRef? = null): EntityRef {
        return createActivity(activityType = "summary", parent = parent)
    }

    private fun createTask(parent: EntityRef? = null, start: Instant? = null, baseEnd: Instant? = null): EntityRef {
        return createActivity(activityType = "task", parent = parent, start = start, baseEnd = baseEnd)
    }

    private fun createActivity(
        activityType: String,
        parent: EntityRef? = null,
        start: Instant? = null,
        baseEnd: Instant? = null
    ): EntityRef {
        return AuthContext.runAsSystem {
            val atts = ObjectData.create()
                .set(RecordConstants.ATT_TYPE, "emodel/type@$TEST_TYPE_ID")
                .set("activity:title", "test-${System.nanoTime()}")
                .set(ATT_TYPE, activityType)
            if (parent != null && parent != EntityRef.EMPTY) {
                atts.set(ATT_PARENT, parent)
            }
            if (start != null) {
                atts.set(ATT_START, start)
            }
            if (baseEnd != null) {
                atts.set(ATT_BASE_END, baseEnd)
            }
            val ref = recordsService.create(TEST_SOURCE_ID, atts)
            refsToDelete.add(ref)
            ref
        }
    }

    private data class Dates(val start: Instant?, val baseEnd: Instant?, val created: Instant?)

    private fun getDates(ref: EntityRef): Dates {
        return AuthContext.runAsSystem {
            val atts = recordsService.getAtts(ref, DatesDto::class.java)
            Dates(atts.start, atts.baseEnd, atts.created)
        }
    }

    private class DatesDto(
        @AttName("_created")
        val created: Instant? = null,
        @AttName(ATT_START)
        val start: Instant? = null,
        @AttName(ATT_BASE_END)
        val baseEnd: Instant? = null
    )

    private fun instantAtUtcDay(year: Int, month: Int, day: Int): Instant {
        return java.time.ZonedDateTime
            .of(year, month, day, 0, 0, 0, 0, java.time.ZoneOffset.UTC)
            .toInstant()
    }
}
