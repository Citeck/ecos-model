package ru.citeck.ecos.model.type;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.association.converter.AssociationConverter;
import ru.citeck.ecos.model.association.domain.AssociationEntity;
import ru.citeck.ecos.model.association.dto.AssociationDto;
import ru.citeck.ecos.model.lib.type.dto.CreateVariantDef;
import ru.citeck.ecos.model.lib.type.dto.DocLibDef;
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef;
import ru.citeck.ecos.model.section.domain.SectionEntity;
import ru.citeck.ecos.model.type.converter.TypeConverter;
import ru.citeck.ecos.model.type.domain.TypeEntity;
import ru.citeck.ecos.model.type.dto.TypeDto;
import ru.citeck.ecos.model.type.dto.TypeWithMetaDto;
import ru.citeck.ecos.model.type.repository.TypeRepository;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
public class TypeConverterTest {

    @MockBean
    private TypeRepository typeRepository;

    @MockBean
    private AssociationConverter associationConverter;

    private TypeConverter typeConverter;

    private TypeEntity typeEntity;
    private TypeWithMetaDto typeDto;

    private TypeEntity parent;

    private RecordRef actionRef;

    private AssociationEntity associationEntity;
    private AssociationDto associationDto;

    private CreateVariantDef createVariantDto1;
    private CreateVariantDef createVariantDto2;

    private final ObjectData configData = Json.getMapper().read("{\n" +
        "  \"color\": \"red\",\n" +
        "  \"icon\": \"urgent\"\n" +
        "}", ObjectData.class);

    @BeforeEach
    void setUp() {
        initCreateVariantDto();
        typeConverter = new TypeConverter(typeRepository, associationConverter);

        TypeEntity child = new TypeEntity();
        child.setExtId("child");

        parent = new TypeEntity();
        parent.setExtId("parent");

        SectionEntity sectionEntity = new SectionEntity();
        sectionEntity.setExtId("section");

        actionRef = RecordRef.create("uiserv", "action", "action");

        TypeEntity baseType = new TypeEntity();
        baseType.setExtId("base");
        when(typeRepository.findByExtId("base")).thenReturn(Optional.of(baseType));

        typeEntity = new TypeEntity();
        typeEntity.setSourceId("");
        typeEntity.setExtId("type");
        typeEntity.setName("{\"en\":\"name\"}");
        typeEntity.setTenant("tenant");
        typeEntity.setDescription("{\"en\":\"desc\"}");
        typeEntity.setChildren(Collections.singleton(child));
        typeEntity.setParent(parent);
        typeEntity.setSystem(false);
        typeEntity.setInheritActions(true);
        typeEntity.setActions("[\"" + actionRef + "\"]");
        typeEntity.setJournal(RecordRef.EMPTY.toString());
        typeEntity.setSections(Collections.singleton(sectionEntity));
        typeEntity.setAttributes("{\"field\":\"value\"}");
        typeEntity.setCreateVariants(Json.getMapper().toString(Arrays.asList(createVariantDto1, createVariantDto2)));
        typeEntity.setAliases(Collections.singleton("alias"));
        typeEntity.setForm("emodel/eform@type-form");
        typeEntity.setConfigForm("emodel/eform@config-form");
        typeEntity.setNumTemplateRef("emodel/num-template@test");
        typeEntity.setConfig(Json.getMapper().toString(configData));
        typeEntity.setModel(Json.getMapper().toString(TypeModelDef.EMPTY));
        typeEntity.setDocLib(Json.getMapper().toString(DocLibDef.EMPTY));
        typeEntity.setDefaultCreateVariant(true);

        associationEntity = new AssociationEntity();
        associationEntity.setExtId("association");
        associationEntity.setTarget(parent);

        typeEntity.setAssociations(Collections.singleton(associationEntity));

        associationDto = new AssociationDto();
        associationDto.setId("association");
        associationDto.setTarget(RecordRef.create("type", "parent"));

        typeDto = new TypeWithMetaDto();
        typeDto.setId("type");
        typeDto.setSourceId("");
        typeDto.setName(new MLText("name"));
        typeDto.setDescription(new MLText("desc"));
        typeDto.setActions(Collections.singletonList(actionRef));
        typeDto.setAssociations(Collections.singletonList(associationDto));
        typeDto.setInheritActions(true);
        typeDto.setInheritForm(true);
        typeDto.setJournal(RecordRef.EMPTY);
        typeDto.setJournal(RecordRef.EMPTY);
        typeDto.setAttributes(Json.getMapper().read("{\"field\":\"value\"}", ObjectData.class));
        typeDto.setParent(RecordRef.create("emodel", "type", parent.getExtId()));
        typeDto.setAliases(Collections.singletonList("alias"));
        typeDto.setCreateVariants(Arrays.asList(createVariantDto1, createVariantDto2));
        typeDto.setForm(RecordRef.create("emodel", "eform", "type-form"));
        typeDto.setConfigFormRef(RecordRef.create("emodel", "eform", "config-form"));
        typeDto.setNumTemplateRef(RecordRef.valueOf("emodel/num-template@test"));
        typeDto.setConfig(Json.getMapper().read("{\n" +
            "  \"color\": \"red\",\n" +
            "  \"icon\": \"urgent\"\n" +
            "}", ObjectData.class));
        typeDto.setModel(TypeModelDef.EMPTY);
        typeDto.setDocLib(DocLibDef.EMPTY);
        typeDto.setDefaultCreateVariant(true);
    }

    @Test
    void testEntityToDto() {

        when(associationConverter.entityToDto(associationEntity)).thenReturn(associationDto);

        TypeDto resultDto = typeConverter.entityToDto(typeEntity);

        Assert.assertEquals(new TypeDto(typeDto), new TypeDto(resultDto));
    }

    @Test
    void testEntityToDtoWithoutParentAndAssociationsAndActionsAndAttributesAndCreateVariants() {
        typeEntity.setParent(null);
        typeEntity.setAssociations(Collections.emptySet());
        typeEntity.setActions(null);
        typeEntity.setCreateVariants(null);
        typeEntity.setAttributes(null);
        typeEntity.setAliases(Collections.emptySet());

        typeDto.setParent(RecordRef.create("emodel", "type", "base"));
        typeDto.setAssociations(Collections.emptyList());
        typeDto.setActions(Collections.emptyList());
        typeDto.setCreateVariants(Collections.emptyList());
        typeDto.setAttributes(null);
        typeDto.setAliases(Collections.emptyList());

        TypeDto resultDto = typeConverter.entityToDto(typeEntity);

        Assert.assertEquals(new TypeDto(typeDto), new TypeDto(resultDto));
    }

    @Test
    void testDtoToEntity() {
        when(associationConverter.dtoToEntity(typeEntity, associationDto)).thenReturn(associationEntity);
        when(typeRepository.findByExtId(parent.getExtId())).thenReturn(Optional.of(parent));
        when(typeRepository.findByExtId(typeEntity.getExtId())).thenReturn(Optional.of(typeEntity));

        TypeEntity resultEntity = typeConverter.dtoToEntity(typeDto);

        Assert.assertEquals(typeDto.getId(), resultEntity.getExtId());
        Assert.assertEquals(typeDto.getName(), Json.getMapper().read(resultEntity.getName(), MLText.class));
        Assert.assertEquals(typeDto.getDescription(), Json.getMapper().read(resultEntity.getDescription(),
            MLText.class));
        Assert.assertEquals(typeDto.getAssociations().size(), resultEntity.getAssociations().size());
        Assert.assertEquals(typeDto.getActions(), Arrays.asList(Json.getMapper().read(resultEntity.getActions(),
            RecordRef[].class)));
        Assert.assertEquals(1, resultEntity.getChildren().size());
        Assert.assertEquals(parent, resultEntity.getParent());
        Assert.assertEquals(1, resultEntity.getSections().size());
        Assert.assertEquals(1, resultEntity.getAliases().size());
        Assert.assertEquals(typeDto.getFormRef(), RecordRef.valueOf(resultEntity.getForm()));
        Assert.assertEquals(typeDto.getConfigFormRef(), RecordRef.valueOf(resultEntity.getConfigForm()));
        Assert.assertEquals(typeDto.getCreateVariants(),
            Arrays.asList(Json.getMapper().read(resultEntity.getCreateVariants(), CreateVariantDef[].class)));
        Assert.assertEquals(typeDto.getAttributes(), Json.getMapper().read(resultEntity.getAttributes(),
            ObjectData.class));
        Assert.assertEquals(typeDto.isInheritActions(), resultEntity.isInheritActions());
        Assert.assertEquals(typeDto.getJournalRef(), RecordRef.valueOf(resultEntity.getJournal()));
        Assert.assertEquals(typeDto.getAliases(), resultEntity.getAliases().stream().collect(Collectors.toList()));
    }

    @Test
    void testDtoToEntityWithoutParentAndAssociationsAndExtIdAndActions() {

        when(typeRepository.findByExtId(parent.getExtId())).thenReturn(Optional.of(parent));

        typeDto.setActions(Collections.emptyList());
        typeDto.setParent(RecordRef.create("emodel", "type", parent.getExtId()));
        typeDto.setId(Strings.EMPTY);
        typeDto.setAssociations(Collections.emptyList());

        TypeEntity resultEntity = typeConverter.dtoToEntity(typeDto);

        Assert.assertTrue(StringUtils.isNotBlank(resultEntity.getExtId()));
        Assert.assertEquals(0, resultEntity.getChildren().size());
        Assert.assertEquals(0, resultEntity.getSections().size());
        Assert.assertEquals(typeDto.getActions(), Arrays.asList(Json.getMapper().read(resultEntity.getActions(),
            RecordRef[].class)));

        Assert.assertEquals(typeDto.getName(), Json.getMapper().read(resultEntity.getName(), MLText.class));
        Assert.assertEquals(typeDto.getDescription(), Json.getMapper().read(resultEntity.getDescription(),
            MLText.class));
        Assert.assertEquals(typeDto.getAssociations().size(), resultEntity.getAssociations().size());
        Assert.assertEquals(parent, resultEntity.getParent());
        Assert.assertEquals(1, resultEntity.getAliases().size());
        Assert.assertEquals(typeDto.getFormRef(), RecordRef.valueOf(resultEntity.getForm()));
        Assert.assertEquals(typeDto.getConfigFormRef(), RecordRef.valueOf(resultEntity.getConfigForm()));
        Assert.assertEquals(typeDto.getCreateVariants(),
            Arrays.asList(Json.getMapper().read(resultEntity.getCreateVariants(), CreateVariantDef[].class)));
        Assert.assertEquals(typeDto.getAttributes(), Json.getMapper().read(resultEntity.getAttributes(),
            ObjectData.class));
        Assert.assertEquals(typeDto.isInheritActions(), resultEntity.isInheritActions());
        Assert.assertEquals(typeDto.getJournalRef(), RecordRef.valueOf(resultEntity.getJournal()));
        Assert.assertEquals(typeDto.getAliases(), resultEntity.getAliases().stream().collect(Collectors.toList()));
    }

    private void initCreateVariantDto() {

        createVariantDto1 = CreateVariantDef.create()
            .withId("id1")
            .withName(new MLText("name1"))
            .withFormRef(RecordRef.create("eform", "cr-form-1"))
            .withSourceId("cr-ref-1")
            .withAttributes(Json.getMapper().read("{\n" +
            "  \"key1\": \"value1\",\n" +
            "  \"key2\": \"value2\"\n" +
            "}", ObjectData.class))
            .build();

        createVariantDto2 = CreateVariantDef.create()
            .withId("id2")
            .withName(new MLText("name2"))
            .withFormRef(RecordRef.create("eform", "cr-form-2"))
            .withSourceId("cr-ref-2")
            .withAttributes(Json.getMapper().read("{\n" +
                "  \"key3\": \"value3\",\n" +
                "  \"key4\": \"value4\"\n" +
                "}", ObjectData.class))
            .build();
    }
}
