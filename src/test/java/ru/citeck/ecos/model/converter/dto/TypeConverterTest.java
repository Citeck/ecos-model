package ru.citeck.ecos.model.converter.dto;

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
import ru.citeck.ecos.model.association.domain.AssociationEntity;
import ru.citeck.ecos.model.association.dto.AssociationDto;
import ru.citeck.ecos.model.converter.DtoConverter;
import ru.citeck.ecos.model.section.domain.SectionEntity;
import ru.citeck.ecos.model.type.converter.TypeConverter;
import ru.citeck.ecos.model.type.domain.TypeEntity;
import ru.citeck.ecos.model.type.dto.CreateVariantDto;
import ru.citeck.ecos.model.type.dto.TypeDto;
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
    private DtoConverter<AssociationDto, AssociationEntity> associationConverter;

    private TypeConverter typeConverter;

    private TypeEntity typeEntity;
    private TypeDto typeDto;

    private TypeEntity parent;

    private RecordRef actionRef;

    private AssociationEntity associationEntity;
    private AssociationDto associationDto;

    private CreateVariantDto createVariantDto1;
    private CreateVariantDto createVariantDto2;

    private final ObjectData[] createVariantsData = Json.getMapper().read("[\n" +
        "  {\n" +
        "    \"id\": \"id1\",\n" +
        "    \"name\": {\n" +
        "      \"en\": \"name1\"\n" +
        "    },\n" +
        "    \"formRef\": \"eform@cr-form-1\",\n" +
        "    \"recordRef\": \"type@cr-ref-1\",\n" +
        "    \"attributes\": {\n" +
        "      \"key1\": \"value1\",\n" +
        "      \"key2\": \"value2\"\n" +
        "    }\n" +
        "  },\n" +
        "  {\n" +
        "    \"id\": \"id2\",\n" +
        "    \"name\": {\n" +
        "      \"en\": \"name2\"\n" +
        "    },\n" +
        "    \"formRef\": \"eform@cr-form-2\",\n" +
        "    \"recordRef\": \"type@cr-ref-2\",\n" +
        "    \"attributes\": {\n" +
        "      \"key3\": \"value3\",\n" +
        "      \"key4\": \"value4\"\n" +
        "    }\n" +
        "  }\n" +
        "]", ObjectData[].class);

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
        typeEntity.setCreateVariants(Json.getMapper().toString(createVariantsData));
        typeEntity.setAliases(Collections.singleton("alias"));
        typeEntity.setForm("emodel/eform@type-form");
        typeEntity.setConfigForm("emodel/eform@config-form");
        typeEntity.setConfig(Json.getMapper().toString(configData));

        associationEntity = new AssociationEntity();
        associationEntity.setExtId("association");
        associationEntity.setTarget(parent);

        typeEntity.setAssocsToOthers(Collections.singleton(associationEntity));

        associationDto = new AssociationDto();
        associationDto.setId("association");
        associationDto.setTarget(RecordRef.create("type", "parent"));

        typeDto = new TypeDto();
        typeDto.setId("type");
        typeDto.setName(new MLText("name"));
        typeDto.setDescription(new MLText("desc"));
        typeDto.setTenant("tenant");
        typeDto.setActions(Collections.singletonList(actionRef));
        typeDto.setAssociations(Collections.singletonList(associationDto));
        typeDto.setInheritActions(true);
        typeDto.setJournal(RecordRef.EMPTY);
        typeDto.setAttributes(Json.getMapper().read("{\"field\":\"value\"}", ObjectData.class));
        typeDto.setParent(RecordRef.create("emodel", "type", parent.getExtId()));
        typeDto.setAliases(Collections.singletonList("alias"));
        typeDto.setCreateVariants(Arrays.asList(createVariantDto1, createVariantDto2));
        typeDto.setForm(RecordRef.create("emodel", "eform", "type-form"));
        typeDto.setConfigForm(RecordRef.create("emodel", "eform", "config-form"));
        typeDto.setConfig(Json.getMapper().read("{\n" +
            "  \"color\": \"red\",\n" +
            "  \"icon\": \"urgent\"\n" +
            "}", ObjectData.class));
    }

    @Test
    void testEntityToDto() {
        when(associationConverter.entityToDto(associationEntity)).thenReturn(associationDto);

        TypeDto resultDto = typeConverter.entityToDto(typeEntity);

        Assert.assertEquals(typeDto, resultDto);
    }

    @Test
    void testEntityToDtoWithoutParentAndAssociationsAndActionsAndAttributesAndCreateVariants() {
        typeEntity.setParent(null);
        typeEntity.setAssocsToOthers(Collections.emptySet());
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

        Assert.assertEquals(typeDto, resultDto);
    }

    @Test
    void testDtoToEntity() {
        when(associationConverter.dtoToEntity(associationDto)).thenReturn(associationEntity);
        when(typeRepository.findByExtId(parent.getExtId())).thenReturn(Optional.of(parent));
        when(typeRepository.findByExtId(typeEntity.getExtId())).thenReturn(Optional.of(typeEntity));

        TypeEntity resultEntity = typeConverter.dtoToEntity(typeDto);

        Assert.assertEquals(typeDto.getId(), resultEntity.getExtId());
        Assert.assertEquals(typeDto.getName(), Json.getMapper().read(resultEntity.getName(), MLText.class));
        Assert.assertEquals(typeDto.getDescription(), Json.getMapper().read(resultEntity.getDescription(),
            MLText.class));
        Assert.assertEquals(typeDto.getAssociations().size(), resultEntity.getAssocsToOthers().size());
        Assert.assertEquals(typeDto.getActions(), Arrays.asList(Json.getMapper().read(resultEntity.getActions(),
            RecordRef[].class)));
        Assert.assertEquals(1, resultEntity.getChildren().size());
        Assert.assertEquals(parent, resultEntity.getParent());
        Assert.assertEquals(1, resultEntity.getSections().size());
        Assert.assertEquals(1, resultEntity.getAliases().size());
        Assert.assertEquals(typeDto.getForm(), RecordRef.valueOf(resultEntity.getForm()));
        Assert.assertEquals(typeDto.getConfigForm(), RecordRef.valueOf(resultEntity.getConfigForm()));
        Assert.assertEquals(typeDto.getCreateVariants(),
            Arrays.asList(Json.getMapper().read(resultEntity.getCreateVariants(), CreateVariantDto[].class)));
        Assert.assertEquals(typeDto.getAttributes(), Json.getMapper().read(resultEntity.getAttributes(),
            ObjectData.class));
        Assert.assertEquals(typeDto.getTenant(), resultEntity.getTenant());
        Assert.assertEquals(typeDto.isInheritActions(), resultEntity.isInheritActions());
        Assert.assertEquals(typeDto.getJournal(), RecordRef.valueOf(resultEntity.getJournal()));
        Assert.assertEquals(typeDto.getAliases(), resultEntity.getAliases().stream().collect(Collectors.toList()));
    }

    @Test
    void testDtoToEntityWithoutParentAndAssociationsAndExtIdAndActions() {
        typeDto.setActions(Collections.emptyList());
        typeDto.setParent(null);
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
        Assert.assertEquals(typeDto.getAssociations().size(), resultEntity.getAssocsToOthers().size());
        Assert.assertEquals(parent, resultEntity.getParent());
        Assert.assertEquals(1, resultEntity.getAliases().size());
        Assert.assertEquals(typeDto.getForm(), RecordRef.valueOf(resultEntity.getForm()));
        Assert.assertEquals(typeDto.getConfigForm(), RecordRef.valueOf(resultEntity.getConfigForm()));
        Assert.assertEquals(typeDto.getCreateVariants(),
            Arrays.asList(Json.getMapper().read(resultEntity.getCreateVariants(), CreateVariantDto[].class)));
        Assert.assertEquals(typeDto.getAttributes(), Json.getMapper().read(resultEntity.getAttributes(),
            ObjectData.class));
        Assert.assertEquals(typeDto.getTenant(), resultEntity.getTenant());
        Assert.assertEquals(typeDto.isInheritActions(), resultEntity.isInheritActions());
        Assert.assertEquals(typeDto.getJournal(), RecordRef.valueOf(resultEntity.getJournal()));
        Assert.assertEquals(typeDto.getAliases(), resultEntity.getAliases().stream().collect(Collectors.toList()));
    }

    private void initCreateVariantDto() {
        createVariantDto1 = new CreateVariantDto();
        createVariantDto1.setId("id1");
        createVariantDto1.setName(new MLText("name1"));
        createVariantDto1.setFormRef(RecordRef.create("eform", "cr-form-1"));
        createVariantDto1.setRecordRef(RecordRef.create("type", "cr-ref-1"));
        createVariantDto1.setAttributes(Json.getMapper().read("{\n" +
            "  \"key1\": \"value1\",\n" +
            "  \"key2\": \"value2\"\n" +
            "}", ObjectData.class));

        createVariantDto2 = new CreateVariantDto();
        createVariantDto2.setId("id2");
        createVariantDto2.setName(new MLText("name2"));
        createVariantDto2.setFormRef(RecordRef.create("eform", "cr-form-2"));
        createVariantDto2.setRecordRef(RecordRef.create("type", "cr-ref-2"));
        createVariantDto2.setAttributes(Json.getMapper().read("{\n" +
            "  \"key3\": \"value3\",\n" +
            "  \"key4\": \"value4\"\n" +
            "}", ObjectData.class));
    }
}
