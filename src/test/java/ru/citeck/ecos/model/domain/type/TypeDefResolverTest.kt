package ru.citeck.ecos.model.domain.type

import mu.KotlinLogging
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.util.ResourceUtils
import ru.citeck.ecos.commons.io.file.std.EcosStdFile
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.model.type.service.resolver.TypeDefResolver
import ru.citeck.ecos.model.type.service.resolver.TypesProvider
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import java.io.File
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
        log.info { "Found tests: \n${tests.joinToString("\n")}"  }

        for (test in tests) {

            log.info { "Run test: ${test.fileName}" }

            val source = InMemTypesProvider().loadFrom(test.resolve("source"))
            val resolvedTypes = resolver.getResolvedTypes(source.getAll(), source, InMemTypesProvider())

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
            return data.values.filter { it.parentRef.id == typeId }.map { it.id }
        }
    }
}
