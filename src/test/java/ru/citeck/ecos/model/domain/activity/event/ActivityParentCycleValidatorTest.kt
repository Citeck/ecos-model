package ru.citeck.ecos.model.domain.activity.event

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.apps.app.service.LocalAppService
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.exception.I18nRuntimeException
import ru.citeck.ecos.commons.utils.resource.ResourceUtils
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.i18n.I18nContext
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
class ActivityParentCycleValidatorTest {

    companion object {
        private const val TEST_SOURCE_ID = "activity-cycle-test"
        private const val TEST_TYPE_ID = "activity-cycle-test"
        private const val ATT_PARENT = "activity:parent"
    }

    @Autowired
    private lateinit var recordsService: RecordsService

    @Autowired
    private lateinit var localAppService: LocalAppService

    @Autowired
    private lateinit var dbDomainFactory: DbDomainFactory

    private val refsToDelete = mutableListOf<EntityRef>()

    @BeforeAll
    fun setUp() {
        AuthContext.runAsSystem {
            localAppService.deployLocalArtifacts(ResourceUtils.getFile("classpath:eapps/artifacts"))

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
                            withTable("test_activity_cycle")
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
    fun selfParentIsRejected() {
        val a = createActivity(name = "Stage 3")
        val ex = assertThrows<Exception> { setParent(a, a) }
        val i18n = i18nCause(ex)
        assertThat(i18n.messageKey).isEqualTo("ecos-model.activity.parent.self-reference")
        assertThat(i18n.messageArgs).containsEntry("ref", "Stage 3 ($a)")
        assertThat(localizedMessage(i18n, I18nContext.ENGLISH))
            .isEqualTo("Activity 'Stage 3 ($a)' cannot be its own parent")
        assertThat(localizedMessage(i18n, I18nContext.RUSSIAN))
            .isEqualTo("Активность 'Stage 3 ($a)' не может быть своим собственным родителем")
        assertThat(getParent(a)).isEqualTo(EntityRef.EMPTY)
    }

    @Test
    fun twoNodeCycleIsRejected() {
        // start: a (no parent), b -> a
        val a = createActivity(name = "Stage A")
        val b = createActivity(parent = a, name = "Stage B")
        // try a -> b; would form a -> b -> a
        val ex = assertThrows<Exception> { setParent(a, b) }
        val i18n = i18nCause(ex)
        assertThat(i18n.messageKey).isEqualTo("ecos-model.activity.parent.cycle-detected")
        assertThat(i18n.messageArgs).containsEntry("ref", "Stage A ($a)").containsEntry("parent", "Stage B ($b)")
        val ruMsg = localizedMessage(i18n, I18nContext.RUSSIAN)
        assertThat(ruMsg).contains("Обнаружен цикл")
        assertThat(ruMsg).contains("Stage A ($a)")
        assertThat(ruMsg).contains("Stage B ($b)")
        assertThat(getParent(a)).isEqualTo(EntityRef.EMPTY)
        assertThat(getParent(b)).isEqualTo(a)
    }

    @Test
    fun threeNodeCycleIsRejected() {
        // start: c -> b -> a
        val a = createActivity()
        val b = createActivity(parent = a)
        val c = createActivity(parent = b)
        // try a -> c; would form a -> c -> b -> a
        val ex = assertThrows<Exception> { setParent(a, c) }
        assertThat(i18nCause(ex).messageKey).isEqualTo("ecos-model.activity.parent.cycle-detected")
        assertThat(getParent(a)).isEqualTo(EntityRef.EMPTY)
    }

    @Test
    fun linearChainIsAllowed() {
        val a = createActivity()
        val b = createActivity()
        val c = createActivity()

        setParent(b, a)
        setParent(c, b)

        assertThat(getParent(a)).isEqualTo(EntityRef.EMPTY)
        assertThat(getParent(b)).isEqualTo(a)
        assertThat(getParent(c)).isEqualTo(b)
    }

    @Test
    fun reparentToDifferentBranchIsAllowed() {
        // tree: a; b -> a; c -> a; d -> b
        val a = createActivity()
        val b = createActivity(parent = a)
        val c = createActivity(parent = a)
        val d = createActivity(parent = b)
        // move d under c: d -> c -> a
        setParent(d, c)
        assertThat(getParent(d)).isEqualTo(c)
    }

    @Test
    fun chainDeeperThanMaxDepthIsRejected() {
        // Build a chain longer than the validator's MAX_DEPTH (100):
        // a0 (root) <- a1 <- a2 <- ... <- a100. Creating records with parent
        // does not emit RecordChangedEvent, so the chain itself is built freely.
        var prev: EntityRef? = null
        val chain = mutableListOf<EntityRef>()
        for (i in 0..100) {
            val ref = createActivity(parent = prev)
            chain.add(ref)
            prev = ref
        }
        val top = chain.last()

        val x = createActivity(name = "Leaf")
        val ex = assertThrows<Exception> { setParent(x, top) }
        val i18n = i18nCause(ex)
        assertThat(i18n.messageKey).isEqualTo("ecos-model.activity.parent.max-depth-exceeded")
        assertThat(i18n.messageArgs["ref"]).isEqualTo("Leaf ($x)")
        assertThat(i18n.messageArgs["parent"] as String).contains(top.toString())
        assertThat(localizedMessage(i18n, I18nContext.RUSSIAN)).contains("Глубина цепочки")
        assertThat(getParent(x)).isEqualTo(EntityRef.EMPTY)
    }

    @Test
    fun clearingParentIsAllowed() {
        val a = createActivity()
        val b = createActivity(parent = a)
        setParent(b, EntityRef.EMPTY)
        assertThat(getParent(b)).isEqualTo(EntityRef.EMPTY)
    }

    private fun createActivity(parent: EntityRef? = null, name: String? = null): EntityRef {
        return AuthContext.runAsSystem {
            val atts = ObjectData.create()
                .set(RecordConstants.ATT_TYPE, "emodel/type@$TEST_TYPE_ID")
                .set("activity:title", "test-${System.nanoTime()}")
            if (name != null) {
                atts.set("_name", name)
            }
            if (parent != null && parent != EntityRef.EMPTY) {
                atts.set(ATT_PARENT, parent)
            }
            val ref = recordsService.create(TEST_SOURCE_ID, atts)
            refsToDelete.add(ref)
            ref
        }
    }

    private fun setParent(ref: EntityRef, parent: EntityRef) {
        AuthContext.runAsSystem {
            recordsService.mutateAtt(ref, ATT_PARENT, parent)
        }
    }

    private fun getParent(ref: EntityRef): EntityRef {
        return AuthContext.runAsSystem {
            val parentId = recordsService.getAtt(ref, "$ATT_PARENT?id").asText()
            if (parentId.isBlank()) EntityRef.EMPTY else EntityRef.valueOf(parentId)
        }
    }

    private fun i18nCause(ex: Throwable): I18nRuntimeException {
        var cur: Throwable? = ex
        while (cur != null) {
            if (cur is I18nRuntimeException) return cur
            cur = cur.cause
        }
        throw AssertionError("I18nRuntimeException not found in cause chain of $ex", ex)
    }

    private fun localizedMessage(ex: I18nRuntimeException, locale: java.util.Locale): String {
        return I18nContext.getMessage(ex.messageKey, locale, ex.messageArgs)
    }
}
