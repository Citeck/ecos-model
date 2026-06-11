package ru.citeck.ecos.model.type.service.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.model.lib.type.dto.CreateVariantDef
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
        // non-ref fields untouched
        assertThat(result.id).isEqualTo("t")
        assertThat(result.workspace).isEqualTo("ws")
        assertThat(result.name).isEqualTo(MLText("name"))
    }

    @Test
    fun `rewrite skips empty refs`() {
        val typeDef = TypeDef.create()
            .withId("t")
            .withAssociations(listOf(AssocDef.create().withId("as").build()))
            .withCreateVariants(listOf(CreateVariantDef.create().withId("cv").build()))
            .build()

        // transform would corrupt an empty ref (localId "" -> "!"); the guard must keep empties empty
        val result = TypeWorkspaceRefs.rewrite(typeDef) { ref -> ref.withLocalId(ref.getLocalId() + "!") }

        assertThat(result.formRef).isEqualTo(EntityRef.EMPTY)
        assertThat(result.numTemplateRef).isEqualTo(EntityRef.EMPTY)
        assertThat(result.associations[0].target).isEqualTo(EntityRef.EMPTY)
        assertThat(result.associations[0].journals).isEmpty()
        assertThat(result.createVariants[0].postActionRef).isEqualTo(EntityRef.EMPTY)
    }
}
