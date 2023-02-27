package ru.citeck.ecos.model.domain.type

import mu.KotlinLogging
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.util.ResourceUtils
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.io.file.std.EcosStdFile
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.commons.test.EcosWebAppApiMock
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef
import ru.citeck.ecos.model.lib.attributes.dto.AttributeType
import ru.citeck.ecos.model.type.service.resolver.TypeDefResolver
import ru.citeck.ecos.model.type.service.resolver.TypesProvider
import ru.citeck.ecos.records2.source.dao.local.RecordsDaoBuilder
import ru.citeck.ecos.records3.RecordsServiceFactory
import ru.citeck.ecos.webapp.api.EcosWebAppApi
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeAspectDef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals

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
            val resolvedTypes = resolver.getResolvedTypes(source.getAll(), source, InMemTypesProvider())

            val expected = InMemTypesProvider().loadFrom(test.resolve("expected"))
            expected.getAll().forEach { expectedType ->
                val resType = resolvedTypes.find { it.id == expectedType.id }
                assertThat(expectedType).describedAs("type: " + expectedType.id).isEqualTo(resType)
            }
        }
    }

    @Test
    fun test2() {

        val prefix = "vv"
        val att = AttributeDef.create().withId("testAttribute")
            .withType(AttributeType.MLTEXT)
            .withMandatory(true)
            .withMultiple(false)
            .build()
        val systAtt = AttributeDef.create().withId("testSystemAttribute")
            .withType(AttributeType.TEXT)
            .withMandatory(false)
            .withMultiple(true)
            .build()

        val testDto = AspectTestDto(prefix, listOf(att), listOf(systAtt))

        val emodelTestWebApp = EcosWebAppApiMock("emodel")
        val records = object : RecordsServiceFactory() {
            override fun getEcosWebAppApi(): EcosWebAppApi {
                return emodelTestWebApp
            }
        }.recordsServiceV1

        records.register(
            RecordsDaoBuilder.create("aspect")
                .addRecord(
                    "aspect-01",
                    testDto
                )
                .build()
        )

        val aspect = TypeAspectDef.create()
            .withRef(EntityRef.valueOf("emodel/aspect@aspect-01"))
            .withConfig(ObjectData.create("{\"id\":\"\",\"name\":\"zzzz\"}"))
            .build()

        val type = TypeDef.create()
            .withId("testType")
            .withAspects(listOf(aspect))
            .build()

        records.register(
            RecordsDaoBuilder.create("type")
                .addRecord(
                    "type-01",
                    type
                ).build()
        )

        val resolver = TypeDefResolver(records)
        val resolvedTypes = resolver.getResolvedTypes(listOf(type), InMemTypesProvider(), InMemTypesProvider())


        assertEquals(prefix + "_" + (testDto.aspectAttributes[0].id), (resolvedTypes[0].model.attributes[0].id))
        assertEquals((testDto.aspectAttributes[0].mandatory), (resolvedTypes[0].model.attributes[0].mandatory))
        assertEquals((testDto.aspectAttributes[0].multiple), (resolvedTypes[0].model.attributes[0].multiple))

        assertEquals(prefix + "_" + (testDto.aspectSystemAttributes[0].id), (resolvedTypes[0].model.systemAttributes[0].id))
        assertEquals((testDto.aspectSystemAttributes[0].mandatory), (resolvedTypes[0].model.systemAttributes[0].mandatory))
        assertEquals((testDto.aspectSystemAttributes[0].multiple), (resolvedTypes[0].model.systemAttributes[0].multiple))
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

    class AspectTestDto(
        val prefix: String,
        val aspectAttributes: List<AttributeDef>,
        val aspectSystemAttributes: List<AttributeDef>
    )
}
