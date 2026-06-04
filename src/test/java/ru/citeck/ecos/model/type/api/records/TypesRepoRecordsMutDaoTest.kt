package ru.citeck.ecos.model.type.api.records

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.model.lib.type.dto.CreateVariantDef
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.model.type.service.TypesService
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.type.dto.AssocDef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef

class TypesRepoRecordsMutDaoTest {

    private val wsPrefix = "custom-sys-id:"
    private val savedCaptor = argumentCaptor<TypeDef>()
    private val dao = createDao()

    @Test
    fun `mutate rebinds CURRENT_WS parentRef to ws prefix on new workspace-scoped type`() {
        dao.mutateForAnyRes(
            LocalRecordAtts(
                "",
                ObjectData.create()
                    .set("id", "my-type")
                    .set("workspace", "custom")
                    .set("parentRef", "emodel/type@CURRENT_WS:my-parent")
            )
        )

        assertThat(savedCaptor.firstValue.parentRef.toString()).isEqualTo("emodel/type@${wsPrefix}my-parent")
    }

    @Test
    fun `mutate rebinds CURRENT_WS in nested association and create variant refs on new ws type`() {
        dao.mutateForAnyRes(
            LocalRecordAtts(
                "",
                ObjectData.create()
                    .set("id", "my-type")
                    .set("workspace", "custom")
                    .set(
                        "associations",
                        listOf(
                            AssocDef.create()
                                .withId("assoc")
                                .withTarget(EntityRef.valueOf("emodel/type@CURRENT_WS:target-type"))
                                .withJournals(listOf(EntityRef.valueOf("uiserv/journal@CURRENT_WS:assoc-journal")))
                                .build()
                        )
                    )
                    .set(
                        "createVariants",
                        listOf(
                            CreateVariantDef.create()
                                .withId("cv")
                                .withTypeRef(EntityRef.valueOf("emodel/type@CURRENT_WS:cv-type"))
                                .withFormRef(EntityRef.valueOf("uiserv/form@CURRENT_WS:cv-form"))
                                .withPostActionRef(EntityRef.valueOf("uiserv/action@CURRENT_WS:cv-action"))
                                .build()
                        )
                    )
            )
        )

        val saved = savedCaptor.firstValue
        assertThat(saved.associations[0].target.toString()).isEqualTo("emodel/type@${wsPrefix}target-type")
        assertThat(saved.associations[0].journals[0].toString()).isEqualTo("uiserv/journal@${wsPrefix}assoc-journal")
        assertThat(saved.createVariants[0].typeRef.toString()).isEqualTo("emodel/type@${wsPrefix}cv-type")
        assertThat(saved.createVariants[0].formRef.toString()).isEqualTo("uiserv/form@${wsPrefix}cv-form")
        assertThat(saved.createVariants[0].postActionRef.toString()).isEqualTo("uiserv/action@${wsPrefix}cv-action")
    }

    private fun createDao(): TypesRepoRecordsMutDao {
        val workspaceService = mock<WorkspaceService>()
        whenever(workspaceService.replaceCurrentWsPlaceholderToWsPrefix(any(), any())).doAnswer { inv ->
            val id = inv.getArgument<String>(0)
            if (id.startsWith("CURRENT_WS:")) wsPrefix + id.removePrefix("CURRENT_WS:") else id
        }
        whenever(workspaceService.addWsPrefixToId(any(), any())).doAnswer { inv -> inv.getArgument<String>(0) }

        val typeService = mock<TypesService>()
        whenever(typeService.save(savedCaptor.capture(), any())).doAnswer { it.getArgument<TypeDef>(0) }

        return TypesRepoRecordsMutDao(typeService, workspaceService = workspaceService)
    }
}
