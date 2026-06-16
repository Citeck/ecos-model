package ru.citeck.ecos.model.domain.type

import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.util.ResourceUtils
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.io.file.std.EcosStdFile
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.model.lib.ModelServiceFactory
import ru.citeck.ecos.model.lib.aspect.dto.AspectInfo
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.model.type.service.resolver.AspectsProvider
import ru.citeck.ecos.model.type.service.resolver.TypeDefResolver
import ru.citeck.ecos.model.type.service.resolver.TypesProvider
import ru.citeck.ecos.model.type.service.utils.EModelTypeUtils
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.aspect.dto.AspectDef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

class TypeDefResolverTest {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Test
    fun test() {

        val workspaceService = ModelServiceFactory().workspaceService
        val eModelTypeUtils = EModelTypeUtils()
        eModelTypeUtils.workspaceService = workspaceService
        val resolver = TypeDefResolver(workspaceService, eModelTypeUtils)

        val tests = getTests()
        log.info { "Found tests: \n${tests.joinToString("\n")}" }

        for (test in tests) {

            log.info { "Run test: ${test.fileName}" }

            val source = InMemTypesProvider().loadFrom(test.resolve("source"))
            val aspects = InMemAspectsProvider().loadFrom(test.resolve("aspects"))
            val resolvedTypes = resolver.getResolvedTypes(
                source.getAll(),
                source,
                InMemTypesProvider(),
                aspects,
                Duration.ofMinutes(1)
            )

            val expected = InMemTypesProvider().loadFrom(test.resolve("expected"))
            expected.getAll().forEach { expectedType ->
                val resType = resolvedTypes.find { it.id == expectedType.id }
                assertTypesEqual(test.fileName.toString(), expectedType, resType)
            }
        }
    }

    @Test
    fun `targeted storage resolution agrees with full resolution`() {

        // deterministic ws-prefixing so the workspace branch (the only genuinely divergent code
        // between the targeted and full parent-walks) is exercised without a real workspace backend
        val workspaceService = mock<WorkspaceService>()
        whenever(workspaceService.addWsPrefixToId(any(), any())).doAnswer { inv ->
            val localId = inv.getArgument<String>(0)
            val workspace = inv.getArgument<String>(1)
            if (workspace.isBlank() || localId.contains(":")) localId else "$workspace-sys-id:$localId"
        }
        val eModelTypeUtils = EModelTypeUtils()
        eModelTypeUtils.workspaceService = workspaceService
        val resolver = TypeDefResolver(workspaceService, eModelTypeUtils)

        fun typeRef(id: String) = EntityRef.valueOf("emodel/type@$id")

        val prov = InMemTypesProvider()
        // base/authority = DEFAULT roots; user-base = ECOS_MODEL business abstract
        prov.add(TypeDef.create().withId("base").build())
        prov.add(
            TypeDef.create().withId("user-base")
                .withParentRef(typeRef("base"))
                .withStorageType(EModelTypeUtils.STORAGE_TYPE_EMODEL)
                .build()
        )
        // DEFAULT under business abstract -> inherits ECOS_MODEL (auto DAO)
        prov.add(TypeDef.create().withId("biz").withParentRef(typeRef("user-base")).build())
        // DEFAULT directly under base -> stays DEFAULT (no auto DAO, like record-version)
        prov.add(TypeDef.create().withId("sys-under-base").withParentRef(typeRef("base")).build())
        // explicit ECOS_MODEL
        prov.add(
            TypeDef.create().withId("explicit-emodel")
                .withParentRef(typeRef("base"))
                .withStorageType(EModelTypeUtils.STORAGE_TYPE_EMODEL)
                .build()
        )
        // explicit sourceId stays DEFAULT
        prov.add(
            TypeDef.create().withId("with-src")
                .withParentRef(typeRef("base"))
                .withSourceId("emodel/custom")
                .build()
        )
        // ws-scoped type: id gets ws-prefixed, storage forced to ECOS_MODEL
        prov.add(
            TypeDef.create().withId("ws-parent")
                .withParentRef(typeRef("base"))
                .withWorkspace("myws")
                .build()
        )
        // child with blank own workspace under a ws-scoped parent -> inherits parent's workspace
        prov.add(TypeDef.create().withId("ws-child").withParentRef(typeRef("ws-parent")).build())

        val fullResolved = resolver.getResolvedTypes(
            prov.getAll(),
            prov,
            InMemTypesProvider(),
            InMemAspectsProvider(),
            Duration.ofMinutes(1)
        )

        prov.getAll().forEach { raw ->
            val targeted = resolver.resolveStorageTypeAndSourceId(raw, prov)
            val full = fullResolved.find { it.id == targeted.id }
                ?: fail("Full resolution produced no type with id '${targeted.id}'")
            if (targeted.storageType != full.storageType || targeted.sourceId != full.sourceId) {
                fail(
                    "Targeted storage resolution disagrees with full resolution for '${raw.id}': " +
                        "targeted=(${targeted.storageType}, ${targeted.sourceId}) " +
                        "full=(${full.storageType}, ${full.sourceId})"
                )
            }
        }
    }

    private fun assertTypesEqual(testDesc: String, expected: TypeDef, actual: TypeDef?) {
        if (expected == actual) {
            return
        }
        var failMsg = "Test '$testDesc' for type '${expected.id}' assertion failed: "
        if (actual == null) {
            fail("$failMsg expected not null but actual type is null")
        }

        val expectedData = DataValue.create(expected)
        val actualData = DataValue.create(actual)

        fun evalDiff(expected: DataValue, actual: DataValue): Pair<DataValue, DataValue> {
            if (expected.isObject() && actual.isObject()) {
                val newActualVal = DataValue.createObj()
                val newExpectedVal = DataValue.createObj()
                val keys = setOf(
                    *actual.fieldNamesList().toTypedArray(),
                    *expected.fieldNamesList().toTypedArray()
                )
                keys.forEach { key ->
                    if (actual[key] != expected[key]) {
                        newActualVal[key] = actual[key]
                        newExpectedVal[key] = expected[key]
                    }
                }
                return newExpectedVal to newActualVal
            }
            if (expected.isArray() && actual.isArray()) {
                val minSize = min(expected.size(), actual.size())
                if (minSize == 0) {
                    return expected to actual
                }
                val newActualVal = DataValue.createArr()
                val newExpectedVal = DataValue.createArr()
                for (idx in 0 until minSize) {
                    val (diffExpected, diffActual) = evalDiff(expected[idx], actual[idx])
                    newActualVal.add(diffActual)
                    newExpectedVal.add(diffExpected)
                }
                for (idx in minSize until expected.size()) {
                    newExpectedVal.add(expected[idx])
                }
                for (idx in minSize until actual.size()) {
                    newActualVal.add(actual[idx])
                }
                return newExpectedVal to newActualVal
            }
            return expected to actual
        }

        expectedData.forEach { k, v ->
            if (actualData[k] != v) {
                val (newExpected, newActual) = evalDiff(v, actualData[k])
                failMsg += "\n$k doesn't match. Diff:"
                failMsg += "\nexpected: $newExpected \nbut was:  $newActual"
                failMsg += "\nfull actual value: ${actualData[k]}"
            }
        }
        fail(failMsg)
    }

    private fun getTests(): List<Path> {
        val fileRes = ResourceUtils.getFile("classpath:" + this::class.qualifiedName!!.replace('.', '/') + "/tests")
        return fileRes.listFiles()!!.map { it.toPath() }
    }

    class InMemAspectsProvider : AspectsProvider {

        private val data = ConcurrentHashMap<String, AspectDef>()

        fun loadFrom(path: Path): InMemAspectsProvider {
            val dir = EcosStdFile(path.toFile())
            dir.findFiles("*.yml").map {
                Json.mapper.read(it, AspectDef::class.java) ?: error("Invalid aspect file: ${it.getPath()}")
            }.forEach {
                add(it)
            }
            return this
        }

        override fun getAspectInfo(aspectRef: EntityRef): AspectInfo? {
            return data[aspectRef.getLocalId()]?.getAspectInfo()
        }

        fun add(type: AspectDef) {
            data[type.id] = type
        }
    }

    class InMemTypesProvider : TypesProvider {

        private val data = ConcurrentHashMap<String, TypeDef>()

        fun loadFrom(path: Path): InMemTypesProvider {
            val dir = EcosStdFile(path.toFile())
            dir.findFiles("*.yml").map {
                Json.mapper.read(it, TypeDef::class.java) ?: error("Invalid type file: ${it.getPath()}")
            }.forEach {
                add(it)
            }
            return this
        }

        fun getAll(): List<TypeDef> {
            return data.values.toList()
        }

        fun add(type: TypeDef) {
            data[type.id] = type
        }

        override fun get(id: String): TypeDef? {
            return data[id]
        }

        override fun getChildren(typeId: String): List<String> {
            return data.values.filter { it.parentRef.getLocalId() == typeId }.map { it.id }
        }
    }
}
