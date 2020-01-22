package ru.citeck.ecos.model.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.module.ModuleRef;
import ru.citeck.ecos.apps.app.module.type.model.type.CreateVariantDto;
import ru.citeck.ecos.model.dto.TypeCreateVariantDto;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Map;

@Slf4j
@Component
public class CreateVariantConverter implements Converter<CreateVariantDto, TypeCreateVariantDto> {

    private final ObjectMapper objectMapper;

    public CreateVariantConverter() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public TypeCreateVariantDto sourceToTarget(CreateVariantDto variantDto) {

        TypeCreateVariantDto localDto = new TypeCreateVariantDto();

        localDto.setId(variantDto.getId());
        localDto.setName(variantDto.getName());

        ModuleRef formModuleRef = variantDto.getFormRef();
        if (formModuleRef != null) {
            localDto.setFormRef(formModuleRef.toString());
        }

        RecordRef recordRef = variantDto.getRecordRef();
        if (recordRef != null) {
            localDto.setRecordRef(variantDto.getRecordRef().toString());
        }

        Map<String, Object> attributes = variantDto.getAttributes();
        if (MapUtils.isNotEmpty(attributes)) {
            try {
                String mapStr = objectMapper.writeValueAsString(attributes);
                localDto.setAttributes(mapStr);
            } catch (JsonProcessingException jpe) {
                log.error("Cannot serialize map of attributes for create variant with id: " + variantDto.getId());
            }
        }
        return localDto;
    }

    @Override
    public CreateVariantDto targetToSource(TypeCreateVariantDto typeCreateVariantDto) {
        throw new UnsupportedOperationException();
    }
}
