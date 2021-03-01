package ru.citeck.ecos.model.domain.type

import org.junit.jupiter.api.Assertions.assertEquals
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
import ru.citeck.ecos.model.type.api.records.ResolvedTypeRecordsDao
import ru.citeck.ecos.model.type.api.records.TypeInhMixin
import ru.citeck.ecos.model.type.api.records.TypeRecordsDao
import ru.citeck.ecos.model.type.config.TypesConfig
import ru.citeck.ecos.model.type.converter.TypeConverter
import ru.citeck.ecos.model.type.dto.AssocDef
import ru.citeck.ecos.model.type.dto.AssocDirection
import ru.citeck.ecos.model.type.dto.TypeDef
import ru.citeck.ecos.model.type.eapps.handler.TypeArtifactHandler
import ru.citeck.ecos.model.type.service.TypeService
import ru.citeck.ecos.model.type.service.TypeServiceImpl
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.RecordsProperties
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.RecordsServiceFactory
import ru.citeck.ecos.records3.record.request.RequestContext
import java.util.*

class TypeServiceTest {

    private lateinit var recordsServices: RecordsServiceFactory
    private lateinit var typesRepo: TypeRepoMock
    private lateinit var typeService: TypeService
    private lateinit var typeArtifactHandler: TypeArtifactHandler

    private lateinit var records: RecordsService

    @BeforeEach
    fun before() {

        recordsServices = object : RecordsServiceFactory() {
            override fun createProperties(): RecordsProperties {
                val props = super.createProperties()
                props.appInstanceId = "123456"
                props.appName = "emodel"
                return props
            }
        }

        typesRepo = TypeRepoMock(recordsServices)
        val typeConverter = TypeConverter(typesRepo)
        typeService = TypeServiceImpl(typeConverter, typesRepo)
        typeArtifactHandler = TypeArtifactHandler(typeService)

        val typeRecordsDao = TypeRecordsDao(typeService)
        recordsServices.recordsServiceV1.register(typeRecordsDao)
        val resolvedRecordsDao = ResolvedTypeRecordsDao(typeService, typeRecordsDao)
        recordsServices.recordsServiceV1.register(resolvedRecordsDao)
        TypeInhMixin(resolvedRecordsDao, typeRecordsDao)

        TypesConfig().typesMutMetaMixin(typeRecordsDao, typeConverter, resolvedRecordsDao)

        records = recordsServices.recordsServiceV1
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
            withId("test-type")
            withName(MLText.EMPTY
                .withValue(Locale.ENGLISH, "English")
                .withValue(Locale.FRANCE, "France"))
            withActions(listOf(
                RecordRef.valueOf("uiserv/action@test0"),
                RecordRef.valueOf("uiserv/action@test2")
            ))
            withAssociations(listOf(
                AssocDef.create {
                    withId("test")
                    withAttribute("test-att")
                    withName(MLText("test-assoc-name"))
                    withTarget(RecordRef.valueOf("emodel/type@base"))
                    withDirection(AssocDirection.SOURCE)
                }
            ))
            withFormRef(RecordRef.valueOf("uiserv/form@test-form"))
            withConfig(ObjectData.create("{\"aa\":\"bb\"}"))
            withConfigFormRef(RecordRef.valueOf("uiserv/form@config-form"))
            withCreateVariants(listOf(
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
            withDescription(MLText("Description"))
            withDashboardType("dashboard-type")
            withDefaultCreateVariant(true)
            withDispNameTemplate(MLText("Disp name template"))
            withDocLib(DocLibDef.create {
                withFileTypeRefs(listOf(
                    TypeUtils.getTypeRef("test"),
                    TypeUtils.getTypeRef("type")
                ))
                withEnabled(true)
            })
            withInheritActions(true)
            withInheritForm(true)
            withInheritNumTemplate(true)
            withJournalRef(RecordRef.valueOf("journal-ref"))
            withModel(TypeModelDef.create {
                withRoles(listOf(
                    RoleDef.create {
                        withId("role-0")
                        withName(MLText("Role 0"))
                        withAttribute("cm:assignees")
                    },
                    RoleDef.create {
                        withId("role-1")
                        withName(MLText("Role 1"))
                        withAttribute("cm:assignees2")
                    }
                ))
                withAttributes(listOf(
                    AttributeDef.create {
                        withId("attribute-0")
                        withName(MLText("Attribute 0"))
                        withMandatory(true)
                    },
                    AttributeDef.create {
                        withId("attribute-1")
                        withName(MLText("Attribute 1"))
                        withMandatory(false)
                    }
                ))
                withStatuses(listOf(
                    StatusDef.create {
                        withId("status-0")
                        withName(MLText("Status 0"))
                    },
                    StatusDef.create {
                        withId("status-1")
                        withName(MLText("Status 1"))
                    }
                ))
            })
            withNumTemplateRef(RecordRef.valueOf("num-template-ref"))
            withProperties(ObjectData.create("""{"aa":"aaa","bb":"bbb"}"""))
            withSystem(true)
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

        typeArtifactHandler.deployArtifact(typeDef)

        val typeFromService = typeService.getById(typeDef.id)
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
            assocsTypeDef = typeService.getByIdOrNull(assocsTypeDef.parentRef.id)
            if (assocsTypeDef == null && currentId != "base") {
                assocsTypeDef = typeService.getById("base")
            }
        }

        assertEquals(assocsCount, assocs.size())
        assertEquals("test-name", assocs.get(0).asText())
    }
}
