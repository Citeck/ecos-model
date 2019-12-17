package ru.citeck.ecos.model.eapps.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.module.type.model.type.TypeModule;
import ru.citeck.ecos.apps.spring.rabbit.EcosModuleListener;
import ru.citeck.ecos.model.converter.module.ModuleConverter;
import ru.citeck.ecos.model.dto.TypeDto;
import ru.citeck.ecos.model.service.TypeService;

@RequiredArgsConstructor
@Component
public class TypeModuleListener implements EcosModuleListener<TypeModule> {

    private final TypeService typeService;
    private final ModuleConverter<TypeModule, TypeDto> typeModuleConverter;

    @Override
    public void onModulePublished(TypeModule module) {
        TypeDto dto = typeModuleConverter.moduleToDto(module);
        typeService.update(dto);
    }

    @Override
    public void onModuleDeleted(String id) {
        typeService.delete(id);
    }

    @Override
    public Class<TypeModule> getModuleType() {
        return TypeModule.class;
    }
}
