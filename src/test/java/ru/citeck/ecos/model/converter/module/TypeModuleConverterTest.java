package ru.citeck.ecos.model.converter.module;

import ecos.com.fasterxml.jackson210.databind.ObjectMapper;
import ecos.com.fasterxml.jackson210.databind.node.ObjectNode;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.records2.scalar.MLText;
import ru.citeck.ecos.apps.app.module.ModuleRef;
import ru.citeck.ecos.apps.app.module.type.model.type.AssociationDto;
import ru.citeck.ecos.apps.app.module.type.model.type.CreateVariantDto;
import ru.citeck.ecos.apps.app.module.type.model.type.TypeModule;
import ru.citeck.ecos.model.converter.EappsAssociationConverter;
import ru.citeck.ecos.model.converter.CreateVariantConverter;
import ru.citeck.ecos.model.converter.module.impl.TypeModuleConverter;
import ru.citeck.ecos.model.dto.TypeAssociationDto;
import ru.citeck.ecos.model.dto.TypeDto;

import java.util.Collections;

import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
public class TypeModuleConverterTest {

    private TypeModuleConverter typeModuleConverter;

    @MockBean
    private EappsAssociationConverter eappsAssociationConverter;

    @MockBean
    private CreateVariantConverter createVariantConverter;

    private TypeModule typeModule;

    private AssociationDto associationDto;

    @BeforeEach
    void setUp() {
        typeModuleConverter = new TypeModuleConverter(eappsAssociationConverter, createVariantConverter);

        associationDto = new AssociationDto();
        associationDto.setId("assocId");

        typeModule = new TypeModule();
        typeModule.setId("typeId");
        typeModule.setName(new MLText("name"));
        typeModule.setDescription(new MLText("desc"));
        typeModule.setParent(ModuleRef.create("TypeModule", "parent"));
        typeModule.setForm(ModuleRef.create("FormModule", "form"));
        typeModule.setInheritActions(true);
        typeModule.setAssociations(Collections.singletonList(associationDto));
        typeModule.setActions(Collections.singletonList(ModuleRef.create("ActionModule", "action")));

        ObjectNode objectNode = null;
        try {
            objectNode = new ObjectMapper().createObjectNode();
            objectNode.put("field", "value");
        } catch (Exception ignored) { }

        typeModule.setAttributes(objectNode);

        CreateVariantDto createVariantDto = new CreateVariantDto();
        createVariantDto.setId("variantId");

        typeModule.setCreateVariants(Collections.singletonList(createVariantDto));
    }

    @Test
    void testModuleToDto() {

        //  arrange
        TypeAssociationDto associationDto = new TypeAssociationDto();
        associationDto.setId("assocId");
        when(eappsAssociationConverter.sourceToTarget(Mockito.any())).thenReturn(associationDto);

        //  act
        TypeDto resultDto = typeModuleConverter.moduleToDto(typeModule);

        //  assert
        Assert.assertEquals(typeModule.getId(), resultDto.getId());
        Assert.assertEquals(typeModule.getName(), resultDto.getName());
        Assert.assertEquals(typeModule.getDescription(), resultDto.getDescription());
        Assert.assertEquals(typeModule.getForm().toString(), resultDto.getForm());
        Assert.assertEquals(typeModule.getParent().getId(), resultDto.getParent().getId());
        Assert.assertEquals(typeModule.getActions().size(), resultDto.getActions().size());
        Assert.assertEquals(typeModule.getActions().size(), resultDto.getActions().size());
        Assert.assertEquals(typeModule.getAttributes().get("field").asText(), "value");
        Assert.assertEquals(typeModule.getCreateVariants().get(0).getId(), "variantId");
        Assert.assertEquals(
            typeModule.getActions().iterator().next().getId(),
            resultDto.getActions().iterator().next().getId());
        Assert.assertEquals(
            typeModule.getAssociations().iterator().next().getId(),
            resultDto.getAssociations().iterator().next().getId());

    }

    @Test
    void testModuleToDtoWithoutFormAndParentAndAssocsAndActionsAndCreateVariantsAndAttributes() {

        //  arrange
        typeModule.setForm(null);
        typeModule.setParent(null);
        typeModule.setActions(null);
        typeModule.setAssociations(null);
        typeModule.setCreateVariants(null);
        typeModule.setAttributes(null);

        //  act
        TypeDto resultDto = typeModuleConverter.moduleToDto(typeModule);

        //  assert
        Assert.assertEquals(typeModule.getId(), resultDto.getId());
        Assert.assertEquals(typeModule.getName(), resultDto.getName());
        Assert.assertEquals(typeModule.getDescription(), resultDto.getDescription());
        Assert.assertNull(resultDto.getForm());
        Assert.assertNull(resultDto.getParent());
        Assert.assertEquals(Collections.emptySet(), resultDto.getActions());
        Assert.assertEquals(Collections.emptySet(), resultDto.getAssociations());
        Assert.assertNull(resultDto.getAttributes());
        Assert.assertEquals(Collections.emptySet(), resultDto.getCreateVariants());

    }
}
