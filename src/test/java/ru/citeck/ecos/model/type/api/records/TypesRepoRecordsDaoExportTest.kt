package ru.citeck.ecos.model.type.api.records

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import ru.citeck.ecos.commons.data.entity.EntityMeta
import ru.citeck.ecos.model.lib.type.dto.CreateVariantDef
import ru.citeck.ecos.model.lib.type.repo.TypesRepo
import ru.citeck.ecos.model.lib.workspace.WorkspaceService
import ru.citeck.ecos.model.type.service.TypesService
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.type.dto.AssocDef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef

class TypesRepoRecordsDaoExportTest {

    private fun daoWithFakeWorkspaceService(): TypesRepoRecordsDao {
        val workspaceService = mock<WorkspaceService>()
        whenever(workspaceService.replaceWsPrefixToCurrentWsPlaceholder(any())).doAnswer { inv ->
            val id = inv.getArgument<String>(0)
            if (id.startsWith("tr:")) "CURRENT_WS:" + id.removePrefix("tr:") else id
        }
        return TypesRepoRecordsDao(
            typeService = mock<TypesService>(),
            workspaceService = workspaceService,
            typesRepo = mock<TypesRepo>()
        )
    }

    @Test
    fun `getData replaces parent type ws prefix with CURRENT_WS placeholder`() {
        val dao = daoWithFakeWorkspaceService()

        val typeDef = TypeDef.create()
            .withId("lulu")
            .withParentRef(EntityRef.valueOf("emodel/type@tr:lilu"))
            .build()

        val yaml = String(dao.TypeRecord(typeDef, mock<EntityMeta>()).getData())

        assertThat(yaml).contains("emodel/type@CURRENT_WS:lilu")
        assertThat(yaml).doesNotContain("tr:lilu")
    }

    @Test
    fun `getData replaces ws prefix in associations and create variants`() {
        val dao = daoWithFakeWorkspaceService()

        val typeDef = TypeDef.create()
            .withId("lulu")
            .withAssociations(
                listOf(
                    AssocDef.create()
                        .withId("assoc")
                        .withTarget(EntityRef.valueOf("emodel/type@tr:tt"))
                        .withJournals(listOf(EntityRef.valueOf("uiserv/journal@tr:jj")))
                        .build()
                )
            )
            .withCreateVariants(
                listOf(
                    CreateVariantDef.create()
                        .withId("cv")
                        .withTypeRef(EntityRef.valueOf("emodel/type@tr:cvt"))
                        .withFormRef(EntityRef.valueOf("uiserv/form@tr:cvf"))
                        .withPostActionRef(EntityRef.valueOf("uiserv/action@tr:cva"))
                        .build()
                )
            )
            .build()

        val yaml = String(dao.TypeRecord(typeDef, mock<EntityMeta>()).getData())

        assertThat(yaml).contains("emodel/type@CURRENT_WS:tt")
        assertThat(yaml).contains("uiserv/journal@CURRENT_WS:jj")
        assertThat(yaml).contains("emodel/type@CURRENT_WS:cvt")
        assertThat(yaml).contains("uiserv/form@CURRENT_WS:cvf")
        assertThat(yaml).contains("uiserv/action@CURRENT_WS:cva")
        assertThat(yaml).doesNotContain("tr:")
    }
}
