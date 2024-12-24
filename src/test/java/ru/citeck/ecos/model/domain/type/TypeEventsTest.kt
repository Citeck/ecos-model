package ru.citeck.ecos.model.domain.type

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.events2.type.RecordChangedEvent
import ru.citeck.ecos.model.domain.type.testutils.TypeTestBase
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef

class TypeEventsTest : TypeTestBase() {

    @Test
    fun test() {

        val rec = records.create(
            "types-repo",
            mapOf(
                "id" to "test",
                "name" to "test-name",
                "model" to TypeModelDef.create {
                    withAttributes(
                        listOf(
                            AttributeDef.create {
                                this.withId("test")
                                this.withName(MLText("abc"))
                            }
                        )
                    )
                }
            )
        )

        val events = mutableListOf<ObjectData>()
        eventsService.addListener<ObjectData> {
            withEventType(RecordChangedEvent.TYPE)
            withDataClass(ObjectData::class.java)
            withTransactional(true)
            withAttributes(
                mapOf(
                    "id" to "diff.list.def.id",
                    "before" to "diff.list.before?disp",
                    "after" to "diff.list.after?disp"
                )
            )
            withAction {
                events.add(it)
            }
        }

        records.mutate(rec, mapOf("name" to "new-name"))

        assertThat(events).hasSize(1)
        assertThat(events[0]["id"].asText()).isEqualTo("name")
        assertThat(events[0]["before"].getAs(MLText::class.java)).isEqualTo(MLText("test-name"))
        assertThat(events[0]["after"].getAs(MLText::class.java)).isEqualTo(MLText("new-name"))

        /*
        events.clear()

        records.mutate(rec, mapOf("model" to TypeModelDef.create {
            withAttributes(
                listOf(
                    AttributeDef.create {
                        this.withId("test")
                        this.withName(MLText("abcdef"))
                    },
                    AttributeDef.create {
                        this.withId("test222")
                        this.withName(MLText("abcdef"))
                    },
                    AttributeDef.create {
                        this.withId("test24444")
                        this.withName(MLText("abcdewaeqwf"))
                    }
                )
            )
        }))

        val before = arraysToObjects(events[0].get("before").asObjectData().getData())
        val after = arraysToObjects(events[0].get("after").asObjectData().getData())

        val eventsList = mutableListOf<String>()
        normalize("", before, after, eventsList)

        for (msg in eventsList) {
            println(msg)
        }*/

        // println(events)
    }
}
