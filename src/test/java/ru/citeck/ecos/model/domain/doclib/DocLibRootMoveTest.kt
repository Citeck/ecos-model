package ru.citeck.ecos.model.domain.doclib

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.model.domain.doclib.api.records.DocLibRecords
import ru.citeck.ecos.model.lib.type.dto.TypeAspectDef
import ru.citeck.ecos.model.lib.type.dto.WorkspaceScope
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import ru.citeck.ecos.webapp.lib.model.type.registry.EcosTypesRegistry

class DocLibRootMoveTest {

    private val records = mock<RecordsService>()
    private val types = mock<EcosTypesRegistry>()
    private val dao = DocLibRecords(types, mock())

    private fun prepare(scope: WorkspaceScope) {
        whenever(types.getValue("library")).thenReturn(
            TypeDef.create().withId("library").withAspects(
                listOf(
                    TypeAspectDef.create()
                        .withRef(EntityRef.valueOf("emodel/aspect@doclib"))
                        .withConfig(ObjectData.create().set("dirTypeRef", "emodel/type@custom-dir"))
                        .build()
                )
            ).build()
        )
        whenever(types.getValue("custom-dir")).thenReturn(
            TypeDef.create().withId("custom-dir").withSourceId("emodel/custom-dir")
                .withWorkspaceScope(scope).build()
        )
        whenever(records.getAtt(any(), eq("_notExists?bool"))).thenReturn(DataValue.create(false))
        whenever(records.mutate(any(), any<ObjectData>())).thenReturn(EntityRef.valueOf("emodel/custom-dir@item"))
        ReflectionTestUtils.setField(dao, "recordsService", records)
    }

    @ParameterizedTest
    @CsvSource(
        "PUBLIC, default, custom-dir, library$" + "ROOT",
        "PUBLIC, team, file, library$" + "ROOT",
        "PRIVATE, default, custom-dir, library$" + "ROOT",
        "PRIVATE, team, custom-dir, library$" + "ROOT$" + "team",
        "PRIVATE, team, file, library$" + "ROOT$" + "team"
    )
    fun `moving to virtual root preserves record and uses its workspace root`(
        scope: WorkspaceScope,
        workspace: String,
        source: String,
        rootLocalId: String
    ) {
        prepare(scope)
        val innerRef = EntityRef.valueOf("emodel/$source@item")
        val id = "library$$innerRef"
        val result = dao.mutateForAnyRes(
            LocalRecordAtts(
                id,
                ObjectData.create().set("_parent", "emodel/doclib@library$")
                    .set("_workspace", workspace).set("name", "Document")
            )
        )
        val atts = argumentCaptor<ObjectData>()
        verify(records).mutate(eq(innerRef), atts.capture())
        assertThat(atts.firstValue["_parent"].asText()).isEqualTo("emodel/custom-dir@$rootLocalId")
        assertThat(atts.firstValue["_parentAtt"].asText()).isEqualTo("children")
        assertThat(atts.firstValue["name"].asText()).isEqualTo("Document")
        assertThat(result.toString()).isEqualTo("emodel/doclib@$id")
        verify(records, never()).create(any(), any())
    }

    @Test
    fun `missing internal root is created before a move`() {
        prepare(WorkspaceScope.PRIVATE)
        whenever(records.getAtt(any(), eq("_notExists?bool"))).thenReturn(DataValue.create(true))
        dao.mutateForAnyRes(
            LocalRecordAtts(
                "library$" + "emodel/custom-dir@item",
                ObjectData.create().set("_parent", "emodel/doclib@library$").set("_workspace", "team")
            )
        )
        val rootAtts = argumentCaptor<Any>()
        verify(records).create(eq("emodel/custom-dir"), rootAtts.capture())
        val root = rootAtts.firstValue as ObjectData
        assertThat(root["id"].asText()).isEqualTo("library$" + "ROOT$" + "team")
        assertThat(root["_workspace"].asText()).isEqualTo("team")
    }

    @Test
    fun `private root move without workspace fails instead of losing the parent`() {
        prepare(WorkspaceScope.PRIVATE)
        assertThatThrownBy {
            dao.mutateForAnyRes(
                LocalRecordAtts(
                    "library$" + "emodel/custom-dir@item",
                    ObjectData.create().set("_parent", "emodel/doclib@library$")
                )
            )
        }.hasMessageContaining("_workspace att is missing")
        verify(records, never()).mutate(any(), any<ObjectData>())
    }

    @Test
    fun `moving to an ordinary directory keeps its parent reference`() {
        prepare(WorkspaceScope.PUBLIC)
        dao.mutateForAnyRes(
            LocalRecordAtts(
                "library$" + "emodel/custom-dir@item",
                ObjectData.create().set("_parent", "emodel/doclib@library$" + "emodel/custom-dir@target")
            )
        )
        val atts = argumentCaptor<ObjectData>()
        verify(records).mutate(any(), atts.capture())
        assertThat(atts.firstValue["_parent"].asText()).isEqualTo("emodel/custom-dir@target")
    }
}
