package ru.citeck.ecos.model.domain.type

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.model.domain.type.testutils.TypeTestBase
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef
import ru.citeck.ecos.model.lib.attributes.dto.computed.ComputedAttDef
import ru.citeck.ecos.model.lib.attributes.dto.computed.ComputedAttType
import ru.citeck.ecos.model.lib.role.dto.RoleDef
import ru.citeck.ecos.model.lib.status.dto.StatusDef
import ru.citeck.ecos.model.lib.type.dto.CreateVariantDef
import ru.citeck.ecos.model.lib.type.dto.DocLibDef
import ru.citeck.ecos.model.lib.type.dto.TypeContentConfig
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils
import ru.citeck.ecos.model.lib.utils.ModelUtils
import ru.citeck.ecos.model.type.service.TypeId
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.type.dto.AssocDef
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef
import java.util.*

class TypesServiceTest : TypeTestBase() {

    @Test
    fun test() {

        val baseType = TypeDef.create {
            withId("base")
            withName(MLText("Base Type"))
            withAssociations(
                listOf(
                    AssocDef.create {
                        withId("base-test")
                        withName(MLText("test-name"))
                        withTarget(EntityRef.create(EcosModelApp.NAME, "type", "assocTarget"))
                    }
                )
            )
        }

        testType(baseType)

        val assocTargetType = TypeDef.create {
            withId("assocTarget")
            withJournalRef(EntityRef.create("uiserv", "journal", "someJournal"))
        }
        artifactHandler.deployArtifact(assocTargetType)

        val fullType = TypeDef.create {
            this.withId("test-type")
            this.withName(
                MLText.EMPTY
                    .withValue(Locale.ENGLISH, "English")
                    .withValue(Locale.FRANCE, "France")
            )
            this.withActions(
                listOf(
                    EntityRef.valueOf("uiserv/action@test0"),
                    EntityRef.valueOf("uiserv/action@test2")
                )
            )
            this.withAssociations(
                listOf(
                    AssocDef.create {
                        this.withId("test")
                        this.withAttribute("test-att")
                        this.withName(MLText("test-assoc-name"))
                        this.withTarget(EntityRef.valueOf("emodel/type@base"))
                        this.withDirection(AssocDef.Direction.SOURCE)
                    }
                )
            )
            this.withFormRef(EntityRef.valueOf("uiserv/form@test-form"))
            this.withConfig(ObjectData.create("{\"aa\":\"bb\"}"))
            this.withConfigFormRef(EntityRef.valueOf("uiserv/form@config-form"))
            this.withCreateVariants(
                listOf(
                    CreateVariantDef.create {
                        withId("create-0")
                        withFormRef(EntityRef.valueOf("uiserv/form@cv-form-0"))
                        withName(MLText("cv-0-name"))
                    },
                    CreateVariantDef.create {
                        withId("create-1")
                        withFormRef(EntityRef.valueOf("uiserv/form@cv-form-1"))
                        withName(MLText("cv-1-name"))
                    }
                )
            )
            this.withDescription(MLText("Description"))
            this.withDashboardType("dashboard-type")
            this.withDefaultCreateVariant(true)
            this.withDispNameTemplate(MLText("Disp name template"))
            this.withDocLib(
                DocLibDef.create {
                    withFileTypeRefs(
                        listOf(
                            ModelUtils.getTypeRef("test"),
                            ModelUtils.getTypeRef("type")
                        )
                    )
                    withEnabled(true)
                }
            )
            this.withContentConfig(
                TypeContentConfig.create {
                    withPath("test-content-main-path")
                    withPreviewPath("test-content-preview-path")
                }
            )
            this.withInheritActions(true)
            this.withInheritForm(true)
            this.withInheritNumTemplate(true)
            this.withJournalRef(EntityRef.valueOf("uiserv/journal@journal-ref"))
            this.withModel(
                TypeModelDef.create {
                    this.withRoles(
                        listOf(
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
                        )
                    )
                    this.withAttributes(
                        listOf(
                            AttributeDef.create {
                                this.withId("attribute-0")
                                this.withName(MLText("Attribute 0"))
                                this.withMandatory(true)
                            },
                            AttributeDef.create {
                                withId("attribute-1")
                                withName(MLText("Attribute 1"))
                                withMandatory(false)
                            },
                            AttributeDef.create {
                                withId("attribute-2-computed")
                                withName(MLText("Attribute 1"))
                                withComputed(
                                    ComputedAttDef.create()
                                        .withType(ComputedAttType.SCRIPT)
                                        .withConfig(ObjectData.create("""{"fn":"return true;"}"""))
                                        .build()
                                )
                            },
                        )
                    )
                    this.withStatuses(
                        listOf(
                            StatusDef.create {
                                this.withId("status-0")
                                this.withName(MLText("Status 0"))
                            },
                            StatusDef.create {
                                this.withId("status-1")
                                this.withName(MLText("Status 1"))
                            }
                        )
                    )
                }
            )
            this.withNumTemplateRef(EntityRef.valueOf("emodel/num-template@num-template-ref"))
            this.withProperties(ObjectData.create("""{"aa":"aaa","bb":"bbb"}"""))
            this.withSystem(true)
        }

        testType(fullType)

        val childType = TypeDef.create()
            .withId("child")
            .withInheritForm(true)
            .withName(MLText("child"))
            .withParentRef(ModelUtils.getTypeRef(fullType.id))
            .withProperties(ObjectData.create("""{"aa":"child_aaa"}"""))
            .build()

        testType(childType)
        val childRef = TypeUtils.getTypeRef(childType.id)

        assertEquals("", records.getAtt(childRef.withSourceId("types-repo"), "formRef?id").asText())
        assertEquals(fullType.formRef.toString(), records.getAtt(childRef, "inhFormRef?id").asText())
        assertEquals(DataValue.create("""{"aa":"child_aaa"}"""), records.getAtt(childRef.withSourceId("types-repo"), "properties?json"))
        assertEquals(DataValue.create("""{"aa":"child_aaa","bb":"bbb"}"""), records.getAtt(childRef, "inhAttributes?json"))

        val childType2 = TypeDef.create()
            .withId("child2")
            .withName(MLText("child2"))
            .withParentRef(ModelUtils.getTypeRef(fullType.id))
            .build()

        testType(childType2)
        val child2Ref = ModelUtils.getTypeRef(childType2.id)

        assertEquals("", records.getAtt(child2Ref.withSourceId("types-repo"), "formRef?id").asText())
        assertEquals("uiserv/form@test-form", records.getAtt(child2Ref, "inhFormRef?id").asText())
    }

    private fun testType(typeDef: TypeDef) {

        artifactHandler.deployArtifact(typeDef)

        val typeFromService = typeService.getById(TypeId.create(typeDef.id))
        assertEquals(typeDef, typeFromService)

        val typeRef = ModelUtils.getTypeRef(typeDef.id).withSourceId("types-repo")

        val typeFromRecords = records.getAtts(typeRef, TypeDef::class.java)
        assertEquals(typeDef, typeFromRecords)

        assertEquals("emodel/type@type", records.getAtt(typeRef, "_type?id").asText())

        val displayName = MLText.getClosestValue(typeDef.name, I18nContext.getLocale())
        assertEquals(displayName, records.getAtt(typeRef, "?disp").asText())
        assertEquals(displayName, records.getAtt(typeRef, "_disp").asText())

        val assocs = records.getAtt(
            EntityRef.create(EcosModelApp.NAME, "rtype", typeRef.getLocalId()),
            "assocsFull[].name"
        )

        var assocsCount = 0
        var assocsTypeDef: TypeDef? = typeDef

        while (assocsTypeDef != null) {
            assocsCount += assocsTypeDef.associations.size
            val currentId = assocsTypeDef.id
            assocsTypeDef = typeService.getByIdOrNull(TypeId.create(assocsTypeDef.parentRef.getLocalId()))
            if (assocsTypeDef == null && currentId != "base") {
                assocsTypeDef = typeService.getById(TypeId.create("base"))
            }
        }

        if (typeDef.id != "base") {
            assertEquals(assocsCount, assocs.size())
            assertEquals("test-name", assocs[0].asText())
        } else {
            // associations targets is not exists yet and associations will be filtered
            assertEquals(0, assocs.size())
        }

        val parents = records.getAtt(typeRef.withSourceId("type"), "parents[]?id").asList(EntityRef::class.java)
        assertTrue(parents.contains(ModelUtils.getTypeRef("base")))

        typeDef.createVariants.forEach {

            val createVariant = records.getAtt(typeRef, "createVariantsById." + it.id + "?json")
                .getAs(CreateVariantDef::class.java)

            assertEquals(it, createVariant)
        }
    }

    @Test
    fun testInhNumTemplate() {

        artifactHandler.deployArtifact(TypeDef.create { withId("base") })

        val custom0 = TypeDef.create {
            withId("custom0")
            withNumTemplateRef(EntityRef.valueOf("emodel/num-template@numTemplateRefValue"))
        }
        artifactHandler.deployArtifact(custom0)
        val custom0Ref = EntityRef.valueOf("emodel/type@custom0")
        val getCustom0AttStr = { att: String ->
            records.getAtt(custom0Ref, att).asText()
        }

        assertEquals(custom0.numTemplateRef.toString(), getCustom0AttStr("numTemplateRef?id"))
        assertEquals(custom0.numTemplateRef.toString(), getCustom0AttStr("inhNumTemplateRef?id"))
        val typeDto0 = records.getAtts(custom0Ref, TypeDto0::class.java)
        assertEquals(custom0.numTemplateRef.toString(), typeDto0.inhNumTemplateRef.toString())

        val custom1 = TypeDef.create {
            withId("custom1")
            withNumTemplateRef(EntityRef.valueOf("emodel/num-template@numTemplateRefValue1123"))
            withInheritNumTemplate(false)
        }
        artifactHandler.deployArtifact(custom1)
        val custom1Ref = EntityRef.valueOf("emodel/type@custom1")
        val getCustom1AttStr = { att: String ->
            records.getAtt(custom1Ref, att).asText()
        }

        assertEquals(custom1.numTemplateRef.toString(), getCustom1AttStr("numTemplateRef?id"))
        assertEquals(custom1.numTemplateRef.toString(), getCustom1AttStr("inhNumTemplateRef?id"))
        val typeDto1 = records.getAtts(custom1Ref, TypeDto0::class.java)
        assertEquals(custom1.numTemplateRef.toString(), typeDto1.inhNumTemplateRef.toString())
    }

    class TypeDto0 {
        var inhNumTemplateRef: EntityRef = EntityRef.EMPTY
    }
}
