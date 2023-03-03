package ru.citeck.ecos.model.domain.type

import mu.KotlinLogging
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.util.ResourceUtils
import ru.citeck.ecos.commons.io.file.std.EcosStdFile
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.model.lib.aspect.dto.AspectInfo
import ru.citeck.ecos.model.type.service.resolver.AspectsProvider
import ru.citeck.ecos.model.type.service.resolver.TypeDefResolver
import ru.citeck.ecos.model.type.service.resolver.TypesProvider
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.aspect.dto.AspectDef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

class TypeDefResolverTest {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Test
    fun test() {

        val resolver = TypeDefResolver()

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
                aspects
            )

            val expected = InMemTypesProvider().loadFrom(test.resolve("expected"))
            expected.getAll().forEach { expectedType ->
                val resType = resolvedTypes.find { it.id == expectedType.id }
                assertThat(expectedType).describedAs("type: " + expectedType.id).isEqualTo(resType)
            }
        }
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
