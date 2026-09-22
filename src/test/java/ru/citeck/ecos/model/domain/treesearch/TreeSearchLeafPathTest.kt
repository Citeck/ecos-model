package ru.citeck.ecos.model.domain.treesearch

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
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosModelApp::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TreeSearchLeafPathTest {

    companion object {
        private const val TEST_SOURCE_ID = "tree-search-leaf-test"
        private const val TEST_TYPE_ID = "tree-search-leaf-test"

        private const val CATEGORY_SOURCE_ID = "emodel/category"
        private const val ATT_CATEGORY = "has-category:category"
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
                            withTable("test_tree_search_leaf")
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
                        listOf(mapOf("ref" to "emodel/aspect@has-category"))
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
    fun leafCreatedWithCategoryGetsPath() {
        val root = createCategory(null)
        val child = createCategory(root)

        assertThat(getPath(root)).isEmpty()
        assertThat(getPath(child)).containsExactly(root)

        val leaf = createLeaf(child)

        assertLeafPath(leaf, listOf(root, child))
        assertThat(queryByCategory(child)).contains(leaf)
    }

    @Test
    fun leafCategoryChangedLaterGetsPath() {
        val root = createCategory(null)
        val child = createCategory(root)

        assertThat(getPath(child)).containsExactly(root)

        val leaf = createLeaf(null)
        assertThat(getPath(leaf)).isEmpty()

        setCategory(leaf, child)

        assertLeafPath(leaf, listOf(root, child))
        assertThat(queryByCategory(child)).contains(leaf)
    }

    @Test
    fun leafCreatedWithoutCategoryHasNoPath() {
        val leaf = createLeaf(null)
        assertThat(getPath(leaf)).isEmpty()
    }

    @Test
    fun leafCategoryRemovedClearsPath() {
        val root = createCategory(null)
        val child = createCategory(root)

        val leaf = createLeaf(null)
        setCategory(leaf, child)
        assertLeafPath(leaf, listOf(root, child))

        setCategory(leaf, EntityRef.EMPTY)

        assertThat(getPath(leaf)).isEmpty()
        assertThat(queryByCategory(child)).doesNotContain(leaf)
    }

    private fun assertLeafPath(leaf: EntityRef, expectedPath: List<EntityRef>) {
        assertThat(getPath(leaf)).containsExactlyElementsOf(expectedPath)
        assertThat(getPathHash(leaf)).isEqualTo(TreeSearchDesc.calculatePathHash(expectedPath))
        assertThat(hasTreeSearchAspect(leaf)).isTrue()
    }

    /**
     * Root categories in a journal are created without a category parent, so their own
     * tree path stays empty. Nested categories get their path from the parent category.
     */
    private fun createCategory(parent: EntityRef?): EntityRef {
        return AuthContext.runAsSystem {
            val atts = ObjectData.create()
                .set("name", "category-${System.nanoTime()}")
            if (parent != null && EntityRef.isNotEmpty(parent)) {
                atts.set(RecordConstants.ATT_PARENT, parent)
                atts.set(RecordConstants.ATT_PARENT_ATT, "children")
            }
            val ref = recordsService.create(CATEGORY_SOURCE_ID, atts)
            refsToDelete.add(ref)
            ref
        }
    }

    private fun createLeaf(category: EntityRef?): EntityRef {
        return AuthContext.runAsSystem {
            val atts = ObjectData.create()
                .set(RecordConstants.ATT_TYPE, ModelUtils.getTypeRef(TEST_TYPE_ID))
                .set("name", "leaf-${System.nanoTime()}")
            if (category != null && EntityRef.isNotEmpty(category)) {
                atts.set(ATT_CATEGORY, category)
            }
            val ref = recordsService.create(TEST_SOURCE_ID, atts)
            refsToDelete.add(ref)
            ref
        }
    }

    private fun setCategory(leaf: EntityRef, category: EntityRef) {
        AuthContext.runAsSystem {
            recordsService.mutateAtt(leaf, ATT_CATEGORY, category)
        }
    }

    private fun getPath(ref: EntityRef): List<EntityRef> {
        return AuthContext.runAsSystem {
            recordsService.getAtt(ref, "${TreeSearchDesc.ATT_PATH}[]?id")
                .asList(EntityRef::class.java)
        }
    }

    private fun getPathHash(ref: EntityRef): String {
        return AuthContext.runAsSystem {
            recordsService.getAtt(ref, "${TreeSearchDesc.ATT_PATH_HASH}?str!").asText()
        }
    }

    private fun hasTreeSearchAspect(ref: EntityRef): Boolean {
        return AuthContext.runAsSystem {
            recordsService.getAtt(ref, "_aspects._has.${TreeSearchDesc.ASPECT_ID}?bool!").asBoolean()
        }
    }

    private fun queryByCategory(category: EntityRef): List<EntityRef> {
        return AuthContext.runAsSystem {
            recordsService.query(
                RecordsQuery.create()
                    .withSourceId(TEST_SOURCE_ID)
                    .withQuery(Predicates.eq(TreeSearchDesc.ATT_PATH, category.toString()))
                    .build()
            ).getRecords()
        }
    }
}
