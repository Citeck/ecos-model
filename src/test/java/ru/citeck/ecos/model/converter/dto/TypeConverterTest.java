package ru.citeck.ecos.model.converter.dto;

import ecos.com.fasterxml.jackson210.databind.ObjectMapper;
import ecos.com.fasterxml.jackson210.databind.node.ObjectNode;
import org.apache.logging.log4j.util.Strings;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.converter.DtoConverter;
import ru.citeck.ecos.model.type.converter.TypeConverter;
import ru.citeck.ecos.model.association.domain.AssociationEntity;
import ru.citeck.ecos.model.section.domain.SectionEntity;
import ru.citeck.ecos.model.type.domain.TypeEntity;
import ru.citeck.ecos.model.type.dto.TypeDto;
import ru.citeck.ecos.model.association.dto.AssociationDto;
import ru.citeck.ecos.model.type.dto.CreateVariantDto;
import ru.citeck.ecos.model.type.repository.TypeRepository;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
public class TypeConverterTest {

    @MockBean
    private TypeRepository typeRepository;

    @MockBean
    private DtoConverter<AssociationDto, AssociationEntity> associationConverter;

    @MockBean
    private DtoConverter<CreateVariantDto, String> typeCreateVariantConverter;

    private TypeConverter typeConverter;

    private TypeEntity typeEntity;
    private TypeDto typeDto;

    private TypeEntity parent;

    private RecordRef actionRef;

    private AssociationEntity associationEntity;
    private AssociationDto associationDto;
    private CreateVariantDto createVariantDto;

    @BeforeEach
    void setUp() {
        typeConverter = new TypeConverter(typeRepository, associationConverter);

        TypeEntity child = new TypeEntity();
        child.setExtId("child");

        parent = new TypeEntity();
        parent.setExtId("parent");


        SectionEntity sectionEntity = new SectionEntity();
        sectionEntity.setExtId("section");

        createVariantDto = new CreateVariantDto();
        createVariantDto.setId("createVariant");

        ObjectNode objectNode = null;
        try {
            objectNode = new ObjectMapper().createObjectNode();
            objectNode.put("field", "value");
        } catch (Exception ignored) { }

        actionRef = RecordRef.create("uiserv", "action", "action");

        TypeEntity baseType = new TypeEntity();
        baseType.setExtId("base");
        when(typeRepository.findByExtId("base")).thenReturn(Optional.of(baseType));

        typeEntity = new TypeEntity();
        typeEntity.setExtId("type");
        typeEntity.setId(123L);
        typeEntity.setName("name");
        typeEntity.setTenant("tenant");
        typeEntity.setDescription("desc");
        typeEntity.setChildren(Collections.singleton(child));
        typeEntity.setParent(parent);
        typeEntity.setActions("[\"" + actionRef + "\"]");
        typeEntity.setSections(Collections.singleton(sectionEntity));
        typeEntity.setAttributes(objectNode != null ? objectNode.toString() : "");
        typeEntity.setCreateVariants("[\n" +
            "    {\n" +
            "        \"id\": \"id2\",\n" +
            "        \"name\": \"name2\",\n" +
            "        \"formRef\": \"type$formRef1\",\n" +
            "        \"recordRef\": \"type@recordRef1\",\n" +
            "        \"attributes\": {\n" +
            "            \"key\": \"value3\",\n" +
            "            \"key2\": \"value4\"\n" +
            "        }\n" +
            "    },\n" +
            "    {\n" +
            "        \"id\": \"id1\",\n" +
            "        \"name\": \"name1\",\n" +
            "        \"formRef\": \"type$formRef1\",\n" +
            "        \"recordRef\": \"type@recordRef1\",\n" +
            "        \"attributes\": {\n" +
            "            \"key\": \"value\",\n" +
            "            \"key2\": \"value2\"\n" +
            "        }\n" +
            "    }\n" +
            "]");
        typeEntity.setAliases(Collections.singleton("alias"));

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
        typeDto.setActions(Collections.singletonList(actionRef));
        typeDto.setAssociations(Collections.singletonList(associationDto));
        typeDto.setInheritActions(true);
        typeDto.setParent(RecordRef.create("type", parent.getExtId()));
        typeDto.setAliases(Collections.singletonList("alias"));
    }

    @Test
    void testEntityToDto() {

        //  arrange

        when(associationConverter.entityToDto(associationEntity)).thenReturn(associationDto);
        when(typeCreateVariantConverter.entityToDto(Mockito.anyString())).thenReturn(createVariantDto);

        //  act
        TypeDto resultDto = typeConverter.entityToDto(typeEntity);

        //  assert
        Assert.assertEquals(resultDto.getId(), typeEntity.getExtId());
        Assert.assertEquals(resultDto.getName(), Json.getMapper().read(typeEntity.getName(), MLText.class));
        Assert.assertEquals(resultDto.getDescription(), Json.getMapper().read(typeEntity.getDescription(), MLText.class));
        Assert.assertEquals(resultDto.getParent(), RecordRef.create("emodel", "type", parent.getExtId()));
        Assert.assertEquals(resultDto.getActions(), Collections.singletonList(actionRef));
        Assert.assertEquals(resultDto.getAssociations(), Collections.singletonList(associationDto));
        Assert.assertEquals(resultDto.getAttributes().toString(), typeEntity.getAttributes());
        Assert.assertEquals(resultDto.getCreateVariants().iterator().next().getId(), "id2");
    }

    @Test
    void testEntityToDtoWithoutParentAndAssociationsAndActionsAndAttributesAndCreateVariants() {

        //  arrange
        typeEntity.setParent(null);
        typeEntity.setAssocsToOthers(Collections.emptySet());
        typeEntity.setActions(null);
        typeEntity.setCreateVariants(null);
        typeEntity.setAttributes(null);
        typeEntity.setAliases(Collections.emptySet());

        //  act
        TypeDto resultDto = typeConverter.entityToDto(typeEntity);

        //  assert
        Assert.assertEquals(resultDto.getId(), typeEntity.getExtId());
        Assert.assertEquals(resultDto.getName(), Json.getMapper().read(typeEntity.getName(), MLText.class));
        Assert.assertEquals(resultDto.getDescription(), Json.getMapper().read(typeEntity.getDescription(), MLText.class));
        Assert.assertNull(resultDto.getAttributes());
        Assert.assertEquals(resultDto.getCreateVariants(), Collections.emptyList());
        Assert.assertEquals(resultDto.getParent(), RecordRef.valueOf("emodel/type@base"));
        Assert.assertEquals(resultDto.getAssociations(), Collections.emptyList());
        Assert.assertEquals(resultDto.getActions(), Collections.emptyList());
        Assert.assertEquals(Collections.emptyList(), resultDto.getAliases());
    }

    @Test
    void testDtoToEntity() {

        //  arrange
        when(associationConverter.dtoToEntity(associationDto)).thenReturn(associationEntity);
        when(typeCreateVariantConverter.dtoToEntity(createVariantDto)).thenReturn("createVariantSampleString");
        when(typeRepository.findByExtId(parent.getExtId())).thenReturn(Optional.of(parent));
        when(typeRepository.findByExtId(typeEntity.getExtId())).thenReturn(Optional.of(typeEntity));

        //  act
        TypeEntity resultEntity = typeConverter.dtoToEntity(typeDto);

        //  assert
        Assert.assertEquals(resultEntity.getExtId(), typeDto.getId());
        Assert.assertEquals(resultEntity.getId().longValue(), 123L);
        Assert.assertEquals(Json.getMapper().read(resultEntity.getName(), MLText.class), typeDto.getName());
        Assert.assertEquals(Json.getMapper().read(resultEntity.getDescription(), MLText.class), typeDto.getDescription());
        Assert.assertEquals(1, resultEntity.getAssocsToOthers().size());
        Assert.assertEquals(resultEntity.getActions(), "[\"" + actionRef.toString() + "\"]");
        Assert.assertEquals(1, resultEntity.getChildren().size());
        Assert.assertEquals(resultEntity.getParent(), parent);
        Assert.assertEquals(1, resultEntity.getSections().size());
        Assert.assertEquals(1, resultEntity.getAliases().size());
    }

    @Test
    void testDtoToEntityWithoutParentAndAssociationsAndExtIdAndActions() {

        //  arrange
        typeDto.setActions(Collections.emptyList());
        typeDto.setParent(null);
        typeDto.setId(Strings.EMPTY);
        typeDto.setAssociations(Collections.emptyList());

        //  act
        TypeEntity resultEntity = typeConverter.dtoToEntity(typeDto);

        //  assert
        Assert.assertEquals(Json.getMapper().read(resultEntity.getName(), MLText.class), typeDto.getName());
        Assert.assertEquals(Json.getMapper().read(resultEntity.getDescription(), MLText.class), typeDto.getDescription());
        Assert.assertEquals(resultEntity.getChildren(), Collections.emptySet());
        Assert.assertEquals(resultEntity.getSections(), Collections.emptySet());
    }
}
