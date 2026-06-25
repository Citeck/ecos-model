package ru.citeck.ecos.model.type.service.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef
import ru.citeck.ecos.model.lib.attributes.dto.AttributeType
import ru.citeck.ecos.model.lib.type.dto.CreateVariantDef
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.type.dto.AssocDef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef

class TypeWorkspaceRefsTest {

    @Test
    fun `rewrite applies transform to every workspace-scoped ref and leaves other fields untouched`() {
        val typeDef = TypeDef.create()
            .withId("t")
            .withWorkspace("ws")
            .withName(MLText("name"))
            .withParentRef(EntityRef.valueOf("emodel/type@p"))
            .withFormRef(EntityRef.valueOf("uiserv/form@f"))
            .withJournalRef(EntityRef.valueOf("uiserv/journal@j"))
            .withNumTemplateRef(EntityRef.valueOf("emodel/num-template@n"))
            .withBoardRef(EntityRef.valueOf("uiserv/board@b"))
            .withConfigFormRef(EntityRef.valueOf("uiserv/form@cf"))
            .withPostCreateActionRef(EntityRef.valueOf("uiserv/action@pca"))
            .withActions(listOf(EntityRef.valueOf("uiserv/action@a1")))
            .withAssociations(
                listOf(
                    AssocDef.create()
                        .withId("as")
                        .withTarget(EntityRef.valueOf("emodel/type@tg"))
                        .withJournals(listOf(EntityRef.valueOf("uiserv/journal@aj")))
                        .build()
                )
            )
            .withCreateVariants(
                listOf(
                    CreateVariantDef.create()
                        .withId("cv")
                        .withTypeRef(EntityRef.valueOf("emodel/type@cvt"))
                        .withFormRef(EntityRef.valueOf("uiserv/form@cvf"))
                        .withPostActionRef(EntityRef.valueOf("uiserv/action@cva"))
                        .build()
                )
            )
            .withModel(
                TypeModelDef.create()
                    .withAttributes(
                        listOf(
                            typeRefAttribute("assoc", AttributeType.ASSOC, "emodel/type@at"),
                            typeRefAttribute("entityRef", AttributeType.ENTITY_REF, "emodel/type@et"),
                            // non-ref attribute with a typeRef in config must stay untouched
                            AttributeDef.create()
                                .withId("text")
                                .withType(AttributeType.TEXT)
                                .withConfig(ObjectData.create().set("typeRef", "emodel/type@ignored"))
                                .build()
                        )
                    )
                    .withSystemAttributes(
                        listOf(typeRefAttribute("sysAssoc", AttributeType.ASSOC, "emodel/type@sat"))
                    )
                    .build()
            )
            .build()

        val result = TypeWorkspaceRefs.rewrite(typeDef) { ref -> ref.withLocalId(ref.getLocalId() + "!") }

        assertThat(result.parentRef.toString()).isEqualTo("emodel/type@p!")
        assertThat(result.formRef.toString()).isEqualTo("uiserv/form@f!")
        assertThat(result.journalRef.toString()).isEqualTo("uiserv/journal@j!")
        assertThat(result.numTemplateRef.toString()).isEqualTo("emodel/num-template@n!")
        assertThat(result.boardRef.toString()).isEqualTo("uiserv/board@b!")
        assertThat(result.configFormRef.toString()).isEqualTo("uiserv/form@cf!")
        assertThat(result.postCreateActionRef.toString()).isEqualTo("uiserv/action@pca!")
        assertThat(result.actions[0].toString()).isEqualTo("uiserv/action@a1!")
        assertThat(result.associations[0].target.toString()).isEqualTo("emodel/type@tg!")
        assertThat(result.associations[0].journals[0].toString()).isEqualTo("uiserv/journal@aj!")
        assertThat(result.createVariants[0].typeRef.toString()).isEqualTo("emodel/type@cvt!")
        assertThat(result.createVariants[0].formRef.toString()).isEqualTo("uiserv/form@cvf!")
        assertThat(result.createVariants[0].postActionRef.toString()).isEqualTo("uiserv/action@cva!")
        // assoc/entity_ref attribute config.typeRef rewritten in both attributes and systemAttributes
        assertThat(result.model.attributes[0].config["typeRef"].asText()).isEqualTo("emodel/type@at!")
        assertThat(result.model.attributes[1].config["typeRef"].asText()).isEqualTo("emodel/type@et!")
        assertThat(result.model.systemAttributes[0].config["typeRef"].asText()).isEqualTo("emodel/type@sat!")
        // non-ref attribute config.typeRef left untouched
        assertThat(result.model.attributes[2].config["typeRef"].asText()).isEqualTo("emodel/type@ignored")
        // non-ref fields untouched
        assertThat(result.id).isEqualTo("t")
        assertThat(result.workspace).isEqualTo("ws")
        assertThat(result.name).isEqualTo(MLText("name"))
    }

    private fun typeRefAttribute(id: String, type: AttributeType, typeRef: String): AttributeDef {
        return AttributeDef.create()
            .withId(id)
            .withType(type)
            .withConfig(ObjectData.create().set("typeRef", typeRef))
            .build()
    }

    @Test
    fun `rewrite skips empty refs`() {
        val typeDef = TypeDef.create()
            .withId("t")
            .withAssociations(listOf(AssocDef.create().withId("as").build()))
            .withCreateVariants(listOf(CreateVariantDef.create().withId("cv").build()))
            .withModel(
                TypeModelDef.create()
                    .withAttributes(
                        listOf(AttributeDef.create().withId("assoc").withType(AttributeType.ASSOC).build())
                    )
                    .build()
            )
            .build()

        // transform would corrupt an empty ref (localId "" -> "!"); the guard must keep empties empty
        val result = TypeWorkspaceRefs.rewrite(typeDef) { ref -> ref.withLocalId(ref.getLocalId() + "!") }

        assertThat(result.formRef).isEqualTo(EntityRef.EMPTY)
        assertThat(result.numTemplateRef).isEqualTo(EntityRef.EMPTY)
        assertThat(result.associations[0].target).isEqualTo(EntityRef.EMPTY)
        assertThat(result.associations[0].journals).isEmpty()
        assertThat(result.createVariants[0].postActionRef).isEqualTo(EntityRef.EMPTY)
        // assoc attribute without a typeRef in config must stay empty, not become "@!" or similar
        assertThat(result.model.attributes[0].config["typeRef"].asText()).isEqualTo("")
    }
}
