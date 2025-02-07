package ru.citeck.ecos.model.domain.type

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.YamlUtils
import ru.citeck.ecos.model.domain.type.testutils.TypeTestBase
import ru.citeck.ecos.model.lib.type.dto.TypeAspectDef
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.model.type.service.TypeDesc
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.util.*

class TypeRecordsAspectsTest : TypeTestBase() {

    @Test
    fun customAspectsTest() {
        val srcAspectsList = listOf(
            createAspectDef("abc", "abc"),
            *TypeDesc.NON_CUSTOM_ASPECTS.map { createAspectDef(it) }.toTypedArray()
        )

        records.create(
            "emodel/types-repo", ObjectData.create()
                .set("id", "test")
                .set("aspects", srcAspectsList)
        )

        fun assertAspectAdded(aspectId: String, expected: Boolean) {
            val added = records.getAtt(
                "emodel/types-repo@test",
                "aspectCfg$$aspectId\$added?bool"
            ).asBoolean()
            assertThat(added).isEqualTo(expected)
        }

        fun assertAspectCfg(aspectId: String, configName: String, expected: String) {
            val value = records.getAtt(
                "emodel/types-repo@test",
                "aspectCfg$$aspectId\$$configName?str"
            ).asText()
            assertThat(value).isEqualTo(expected)
        }

        assertThat(getTypeAspects("test")).isEqualTo(srcAspectsList)
        assertThat(getTypeAspects("test", true)).containsExactly(srcAspectsList[0])
        srcAspectsList.forEach {
            val aspectId = it.ref.getLocalId()
            if (aspectId == "abc") {
                assertAspectCfg(aspectId, "key", "abc")
            } else {
                assertAspectCfg(aspectId, "key", "value-$aspectId")
            }
            assertAspectAdded(aspectId, true)
        }
        assertAspectAdded("unknown", false)

        records.mutate("emodel/types-repo@test", ObjectData.create().set("aspects", DataValue.createArr()))
        srcAspectsList.forEach {
            assertAspectAdded(it.ref.getLocalId(), false)
        }
        assertThat(getTypeAspects("test")).isEmpty()

        fun mutateType(atts: ObjectData) {
            records.mutate("emodel/types-repo@test", atts)
        }

        mutateType(
            ObjectData.create()
                .set("customAspects", listOf(createAspectDef("abc", "abc")))
                .set("aspectCfg\$listview\$added", true)
        )
        assertThat(getTypeAspects("test")).containsExactly(
            createAspectDef("abc", "abc"),
            createAspectDef("listview", ObjectData.create())
        )

        mutateType(
            ObjectData.create().set(
                "customAspects",
                listOf(createAspectDef("def"))
            )
        )
        assertThat(getTypeAspects("test")).containsExactly(
            createAspectDef("def", "value-def"),
            createAspectDef("listview", ObjectData.create())
        )

        mutateType(
            ObjectData.create()
                .set("aspectCfg\$listview\$added", false)
                .set(
                    "customAspects",
                    listOf(createAspectDef("hij"))
                )
                //
        )
        assertThat(getTypeAspects("test")).containsExactly(
            createAspectDef("hij", "value-hij")
        )

        mutateType(
            ObjectData.create()
                .set("aspectCfg\$listview\$added", true)
                .set("aspectCfg\$listview\$customConfig", "listview-value")
                .set(
                    "customAspects",
                    listOf(createAspectDef("klm"))
                )
        )

        assertThat(getTypeAspects("test")).containsExactly(
            createAspectDef("klm", "value-klm"),
            createAspectDef("listview", ObjectData.create().set("customConfig", "listview-value"))
        )
    }

    @Test
    fun aspectsMutationTest() {

        val srcAspectsList = listOf(
            createAspectDef("abc"),
            createAspectDef("listview"),
            createAspectDef("doclib")
        )

        fun test(
            createAtts: (id: String, aspects: List<TypeAspectDef>) -> ObjectData,
            mutateAtts: (id: String, aspects: List<TypeAspectDef>) -> ObjectData
        ) {
            val type0Id = "test-type-0"
            val type0DataToMut = createAtts(type0Id, srcAspectsList)
            records.create("emodel/types-repo", type0DataToMut)

            assertThat(getTypeAspects(type0Id))
                .hasSize(3)
                .containsExactlyElementsOf(srcAspectsList)

            val newAspectsList = srcAspectsList.toMutableList()
            newAspectsList.add(createAspectDef("new"))
            val type0DataToMut2 = mutateAtts(type0Id, newAspectsList)

            records.mutate(EntityRef.create(AppName.EMODEL, "types-repo", type0Id), type0DataToMut2)
            assertThat(getTypeAspects(type0Id))
                .hasSize(4)
                .containsExactlyElementsOf(newAspectsList)

            records.delete(EntityRef.create(AppName.EMODEL, "types-repo", type0Id))
        }

        test({ id, aspects ->
            ObjectData.create()
                .set("id", id)
                .set("aspects", aspects)
        }) { id, aspects ->
            ObjectData.create()
                .set("id", id)
                .set("aspects", aspects)
        }

        fun createBase64SelfAttYamlContent(data: ObjectData): ObjectData {
            val dataBytes = YamlUtils.toString(data).toByteArray()
            return ObjectData.create()
                .set(
                    ".att(n:\"_self\"){as(n:\"content-data\"){json}}", ObjectData.create()
                        .set("storage", "base64")
                        .set("name", "type-def.yml")
                        .set("url", "data:application/x-yaml;base64," + Base64.getEncoder().encodeToString(dataBytes))
                        .set("size", dataBytes.size)
                        .set("type", "application/x-yaml")
                        .set("originalName", "type-def.yml")
                ).set("_type?str", "emodel/type@type-def")
                .set("_workspace", "admin\$workspace")
        }

        test({ id, aspects ->
            createBase64SelfAttYamlContent(
                ObjectData.create()
                    .set("id", id)
                    .set("aspects", aspects)
            )
        }) { id, aspects ->
            createBase64SelfAttYamlContent(
                ObjectData.create()
                    .set("id", id)
                    .set("aspects", aspects)
            )
        }
    }

    private fun getTypeAspects(typeId: String, custom: Boolean = false): List<TypeAspectDef> {
        val (srcId, attribute) = if (custom) {
            "types-repo" to "customAspects"
        } else {
            "type" to "aspects"
        }
        return records.getAtt(
            EntityRef.create(AppName.EMODEL, srcId, typeId),
            "$attribute[]?json"
        ).asList(TypeAspectDef::class.java)
    }

    private fun createAspectDef(id: String): TypeAspectDef {
        return createAspectDef(id, "value-$id")
    }

    private fun createAspectDef(id: String, configValue: String): TypeAspectDef {
        return createAspectDef(id, ObjectData.create().set("key", configValue))
    }

    private fun createAspectDef(id: String, config: ObjectData): TypeAspectDef {
        return TypeAspectDef.create()
            .withRef(ModelUtils.getAspectRef(id))
            .withConfig(config)
            .build()
    }
}
