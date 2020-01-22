package ru.citeck.ecos.model.converter.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.model.converter.dto.impl.TypeCreateVariantConverter;
import ru.citeck.ecos.model.dto.TypeCreateVariantDto;

@ExtendWith(SpringExtension.class)
public class TypeCreateVariantConverterTest {

    private TypeCreateVariantConverter converter;

    private ObjectMapper objectMapper;

    private TypeCreateVariantDto createVariantDto;

    @BeforeEach
    void setUp() {
        createVariantDto = new TypeCreateVariantDto();
        createVariantDto.setId("createVariant");
        createVariantDto.setName("name");
        createVariantDto.setFormRef("formRef");
        createVariantDto.setRecordRef("recordRef");
        createVariantDto.setAttributes("{\"key\":\"value\"");
        converter = new TypeCreateVariantConverter();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testDtoToEntity() {
        String string = converter.dtoToEntity(createVariantDto);

        Assert.assertEquals(string, "{\"id\":\"createVariant\",\"name\":\"name\",\"formRef\":\"formRef\"" +
            ",\"recordRef\":\"recordRef\",\"attributes\":\"{\\\"key\\\":\\\"value\\\"\"}");
        System.out.println(string);
    }

    @Test
    void entityToDto() {
        TypeCreateVariantDto dto = converter.entityToDto("{\"id\":\"createVariant\",\"name\":\"name\",\"" +
            "formRef\":\"formRef\",\"recordRef\":\"recordRef\",\"attributes\":\"{\\\"key\\\":\\\"value\\\"\"}");

        Assert.assertEquals(dto.getId(), createVariantDto.getId());
        Assert.assertEquals(dto.getName(), createVariantDto.getName());
        Assert.assertEquals(dto.getAttributes(), createVariantDto.getAttributes());
        Assert.assertEquals(dto.getFormRef(), createVariantDto.getFormRef());
        Assert.assertEquals(dto.getRecordRef(), createVariantDto.getRecordRef());
    }
}
