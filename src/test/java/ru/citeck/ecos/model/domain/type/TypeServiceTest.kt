package ru.citeck.ecos.model.domain.type

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef
import ru.citeck.ecos.model.lib.role.dto.RoleDef
import ru.citeck.ecos.model.lib.status.dto.StatusDef
import ru.citeck.ecos.model.lib.type.dto.CreateVariantDef
import ru.citeck.ecos.model.lib.type.dto.DocLibDef
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.model.type.dto.AssocDef
import ru.citeck.ecos.model.type.dto.AssocDirection
import ru.citeck.ecos.model.type.dto.TypeDef
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.request.RequestContext
import java.util.*

class TypeServiceTest {

    private lateinit var records: RecordsService
    private lateinit var services: TypeTestServices

    @BeforeEach
    fun before() {
        services = TypeTestServices()
        records = services.records
    }

    @Test
    fun test() {

        val baseType = TypeDef.create {
            withId("base")
            withName(MLText("Base Type"))
            withAssociations(listOf(
                AssocDef.create {
                    withId("base-test")
                    withName(MLText("test-name"))
                }
            ))
        }

        testType(baseType)

        val fullType = TypeDef.create {
            this.withId("test-type")
            this.withName(MLText.EMPTY
                .withValue(Locale.ENGLISH, "English")
                .withValue(Locale.FRANCE, "France")
            )
            this.withActions(listOf(
                RecordRef.valueOf("uiserv/action@test0"),
                RecordRef.valueOf("uiserv/action@test2")
            ))
            this.withAssociations(listOf(
                AssocDef.create {
                    this.withId("test")
                    this.withAttribute("test-att")
                    this.withName(MLText("test-assoc-name"))
                    this.withTarget(RecordRef.valueOf("emodel/type@base"))
                    this.withDirection(AssocDirection.SOURCE)
                }
            ))
            this.withFormRef(RecordRef.valueOf("uiserv/form@test-form"))
            this.withConfig(ObjectData.create("{\"aa\":\"bb\"}"))
            this.withConfigFormRef(RecordRef.valueOf("uiserv/form@config-form"))
            this.withCreateVariants(listOf(
                CreateVariantDef.create {
                    withId("create-0")
                    withFormRef(RecordRef.valueOf("uiserv/form@cv-form-0"))
                    withName(MLText("cv-0-name"))
                },
                CreateVariantDef.create {
                    withId("create-1")
                    withFormRef(RecordRef.valueOf("uiserv/form@cv-form-1"))
                    withName(MLText("cv-1-name"))
                }
            ))
            this.withDescription(MLText("Description"))
            this.withDashboardType("dashboard-type")
            this.withDefaultCreateVariant(true)
            this.withDispNameTemplate(MLText("Disp name template"))
            this.withDocLib(DocLibDef.create {
                withFileTypeRefs(listOf(
                    TypeUtils.getTypeRef("test"),
                    TypeUtils.getTypeRef("type")
                ))
                withEnabled(true)
            })
            this.withInheritActions(true)
            this.withInheritForm(true)
            this.withInheritNumTemplate(true)
            this.withJournalRef(RecordRef.valueOf("journal-ref"))
            this.withModel(TypeModelDef.create {
                this.withRoles(listOf(
                    RoleDef.create {
                        this.withId("role-0")
                        this.withName(MLText("Role 0"))
                        this.withAttribute("cm:assignees")
                    },
                    RoleDef.create {
                        this.withId("role-1")
                        this.withName(MLText("Role 1"))
                        this.withAttribute("cm:assignees2")
                    }
                ))
                this.withAttributes(listOf(
                    AttributeDef.create {
                        this.withId("attribute-0")
                        this.withName(MLText("Attribute 0"))
                        this.withMandatory(true)
                    },
                    AttributeDef.create {
                        withId("attribute-1")
                        withName(MLText("Attribute 1"))
                        withMandatory(false)
                    }
                ))
                this.withStatuses(listOf(
                    StatusDef.create {
                        this.withId("status-0")
                        this.withName(MLText("Status 0"))
                    },
                    StatusDef.create {
                        this.withId("status-1")
                        this.withName(MLText("Status 1"))
                    }
                ))
            })
            this.withNumTemplateRef(RecordRef.valueOf("num-template-ref"))
            this.withProperties(ObjectData.create("""{"aa":"aaa","bb":"bbb"}"""))
            this.withSystem(true)
        }

        testType(fullType)

        val childType = TypeDef.create()
            .withId("child")
            .withInheritForm(true)
            .withName(MLText("child"))
            .withParentRef(TypeUtils.getTypeRef(fullType.id))
            .withProperties(ObjectData.create("""{"aa":"child_aaa"}"""))
            .build()

        testType(childType)
        val childRef = TypeUtils.getTypeRef(childType.id)

        assertEquals("", records.getAtt(childRef, "formRef?id").asText())
        assertEquals(fullType.formRef.toString(), records.getAtt(childRef, "inhFormRef?id").asText())
        assertEquals(DataValue.create("""{"aa":"child_aaa"}"""), records.getAtt(childRef, "attributes?json"))
        assertEquals(DataValue.create("""{"aa":"child_aaa","bb":"bbb"}"""), records.getAtt(childRef, "inhAttributes?json"))

        val childType2 = TypeDef.create()
            .withId("child2")
            .withName(MLText("child2"))
            .withParentRef(TypeUtils.getTypeRef(fullType.id))
            .build()

        testType(childType2)
        val child2Ref = TypeUtils.getTypeRef(childType2.id)

        assertEquals("", records.getAtt(child2Ref, "formRef?id").asText())
        assertEquals("", records.getAtt(child2Ref, "inhFormRef?id").asText())
    }

    private fun testType(typeDef: TypeDef) {

        services.artifactHandler.deployArtifact(typeDef)

        val typeFromService = services.typeService.getById(typeDef.id)
        assertEquals(typeDef, typeFromService)

        val typeRef = TypeUtils.getTypeRef(typeDef.id)

        val typeFromRecords = records.getAtts(typeRef, TypeDef::class.java)
        assertEquals(typeDef, typeFromRecords)

        assertEquals("emodel/type@type", records.getAtt(typeRef, "_type?id").asText())

        val displayName = MLText.getClosestValue(typeDef.name, RequestContext.getLocale())
        assertEquals(displayName, records.getAtt(typeRef, "?disp").asText())
        assertEquals(displayName, records.getAtt(typeRef, "_disp").asText())

        val assocs = records.getAtt(RecordRef.create("emodel", "rtype", typeRef.id), "assocsFull[].name")

        var assocsCount = 0
        var assocsTypeDef: TypeDef? = typeDef

        while (assocsTypeDef != null) {
            assocsCount += assocsTypeDef.associations.size
            val currentId = assocsTypeDef.id
            assocsTypeDef = services.typeService.getByIdOrNull(assocsTypeDef.parentRef.id)
            if (assocsTypeDef == null && currentId != "base") {
                assocsTypeDef = services.typeService.getById("base")
            }
        }

        assertEquals(assocsCount, assocs.size())
        assertEquals("test-name", assocs.get(0).asText())

        val parents = records.getAtt(typeRef, "parents[]?id").asList(RecordRef::class.java);
        assertTrue(parents.contains(TypeUtils.getTypeRef("base")))

        typeDef.createVariants.forEach {

            val createVariant = records.getAtt(typeRef, "createVariantsById." + it.id + "?json")
                .getAs(CreateVariantDef::class.java)

            assertEquals(it, createVariant)
        }
    }

    @Test
    fun testInhNumTemplate() {

        services.artifactHandler.deployArtifact(TypeDef.create { withId("base") })

        val custom0 = TypeDef.create {
            withId("custom0")
            withNumTemplateRef(RecordRef.valueOf("numTemplateRefValue"))
        };
        services.artifactHandler.deployArtifact(custom0)
        val custom0Ref = RecordRef.valueOf("emodel/type@custom0")
        val getCustom0AttStr = { att: String ->
            services.records.getAtt(custom0Ref, att).asText()
        }

        assertEquals(custom0.numTemplateRef.toString(), getCustom0AttStr("numTemplateRef?id"))
        assertEquals(custom0.numTemplateRef.toString(), getCustom0AttStr("inhNumTemplateRef?id"))
        val typeDto0 = services.records.getAtts(custom0Ref, TypeDto0::class.java)
        assertEquals(custom0.numTemplateRef.toString(), typeDto0.inhNumTemplateRef.toString())

        val custom1 = TypeDef.create {
            withId("custom1")
            withNumTemplateRef(RecordRef.valueOf("numTemplateRefValue1123"))
            withInheritNumTemplate(false)
        };
        services.artifactHandler.deployArtifact(custom1)
        val custom1Ref = RecordRef.valueOf("emodel/type@custom1")
        val getCustom1AttStr = { att: String ->
            services.records.getAtt(custom1Ref, att).asText()
        }

        assertEquals(custom1.numTemplateRef.toString(), getCustom1AttStr("numTemplateRef?id"))
        assertEquals(custom1.numTemplateRef.toString(), getCustom1AttStr("inhNumTemplateRef?id"))
        val typeDto1 = services.records.getAtts(custom1Ref, TypeDto0::class.java)
        assertEquals(custom1.numTemplateRef.toString(), typeDto1.inhNumTemplateRef.toString())
    }

    class TypeDto0 {
        var inhNumTemplateRef: RecordRef = RecordRef.EMPTY
    }
}
