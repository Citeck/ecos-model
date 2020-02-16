package ru.citeck.ecos.model.converter;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.apps.app.module.ModuleRef;
import ru.citeck.ecos.apps.app.module.type.model.type.CreateVariantDto;
import ru.citeck.ecos.model.dto.TypeCreateVariantDto;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.objdata.ObjectData;

import java.util.HashMap;
import java.util.Map;

@ExtendWith(SpringExtension.class)
public class CreateVariantConverterTest {

    private CreateVariantConverter createVariantConverter;

    @BeforeEach
    void setUp() {
        createVariantConverter = new CreateVariantConverter();
    }

    @Test
    void testSourceToTarget() {

        CreateVariantDto createVariantDto = new CreateVariantDto();
        createVariantDto.setId("createVariant");
        createVariantDto.setName("name");
        createVariantDto.setFormRef(ModuleRef.create("type", "id"));
        createVariantDto.setRecordRef(RecordRef.create("source", "id"));

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("field", "value");
        createVariantDto.setAttributes(new ObjectData(attributes));

        TypeCreateVariantDto typeCreateVariantDto = createVariantConverter.sourceToTarget(createVariantDto);
        Assert.assertEquals(typeCreateVariantDto.getId(), createVariantDto.getId());
        Assert.assertEquals(typeCreateVariantDto.getName(), createVariantDto.getName());
        Assert.assertEquals(typeCreateVariantDto.getRecordRef(), createVariantDto.getRecordRef().toString());
        Assert.assertEquals(typeCreateVariantDto.getFormRef(), createVariantDto.getFormRef().toString());

        Assert.assertEquals(typeCreateVariantDto.getAttributes(), new ObjectData(attributes));
        Assert.assertNotSame(typeCreateVariantDto.getAttributes(), attributes);
    }

    @Test
    void testTargetToSource() {
        TypeCreateVariantDto typeCreateVariantDto = new TypeCreateVariantDto();
        try {
            createVariantConverter.targetToSource(typeCreateVariantDto);
        } catch (UnsupportedOperationException uoe) { }
    }
}
