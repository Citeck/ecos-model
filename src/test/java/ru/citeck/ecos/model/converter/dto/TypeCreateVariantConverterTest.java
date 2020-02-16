package ru.citeck.ecos.model.converter.dto;

import ecos.com.fasterxml.jackson210.core.type.TypeReference;
import ecos.com.fasterxml.jackson210.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.model.converter.dto.impl.TypeCreateVariantConverter;
import ru.citeck.ecos.model.dto.TypeCreateVariantDto;
import ru.citeck.ecos.records2.objdata.ObjectData;

import java.io.IOException;
import java.util.Map;

@ExtendWith(SpringExtension.class)
public class TypeCreateVariantConverterTest {

    private TypeCreateVariantConverter converter;

    private ObjectMapper objectMapper;

    private TypeCreateVariantDto createVariantDto;

    @BeforeEach
    void setUp() throws IOException {

        objectMapper = new ObjectMapper();

        createVariantDto = new TypeCreateVariantDto();
        createVariantDto.setId("createVariant");
        createVariantDto.setName("name");
        createVariantDto.setFormRef("formRef");
        createVariantDto.setRecordRef("recordRef");

        Map<String, Object> atts = objectMapper.readValue("{\"key\":\"value\"}",
                                                          new TypeReference<Map<String, Object>>(){});
        createVariantDto.setAttributes(new ObjectData(atts));
        converter = new TypeCreateVariantConverter();
    }

    @Test
    void testDtoToEntity() {
        String string = converter.dtoToEntity(createVariantDto);

        Assert.assertEquals(string, "{\"id\":\"createVariant\",\"name\":\"name\",\"formRef\":\"formRef\"" +
            ",\"recordRef\":\"recordRef\",\"attributes\":{\"key\":\"value\"}}");
        System.out.println(string);
    }

    @Test
    void entityToDto() {
        TypeCreateVariantDto dto = converter.entityToDto("{\"id\":\"createVariant\",\"name\":\"name\",\"" +
            "formRef\":\"formRef\",\"recordRef\":\"recordRef\",\"attributes\":{\"key\":\"value\"}}");

        Assert.assertEquals(dto.getId(), createVariantDto.getId());
        Assert.assertEquals(dto.getName(), createVariantDto.getName());
        Assert.assertEquals(dto.getAttributes(), createVariantDto.getAttributes());
        Assert.assertEquals(dto.getFormRef(), createVariantDto.getFormRef());
        Assert.assertEquals(dto.getRecordRef(), createVariantDto.getRecordRef());
    }
}
