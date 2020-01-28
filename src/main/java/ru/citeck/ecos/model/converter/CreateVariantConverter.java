package ru.citeck.ecos.model.converter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.module.ModuleRef;
import ru.citeck.ecos.apps.app.module.type.model.type.CreateVariantDto;
import ru.citeck.ecos.model.dto.TypeCreateVariantDto;
import ru.citeck.ecos.records2.RecordRef;

@Slf4j
@Component
public class CreateVariantConverter implements Converter<CreateVariantDto, TypeCreateVariantDto> {

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

        localDto.setAttributes(variantDto.getAttributes());

        return localDto;
    }

    @Override
    public CreateVariantDto targetToSource(TypeCreateVariantDto typeCreateVariantDto) {
        throw new UnsupportedOperationException();
    }
}
