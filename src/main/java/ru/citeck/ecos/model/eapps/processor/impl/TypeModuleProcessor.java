package ru.citeck.ecos.model.eapps.processor.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.module.type.type.TypeModule;
import ru.citeck.ecos.model.dto.TypeDto;
import ru.citeck.ecos.model.eapps.processor.ModuleProcessor;
import ru.citeck.ecos.model.service.TypeService;

@Component
@Slf4j
public class TypeModuleProcessor implements ModuleProcessor<TypeModule> {

    private final TypeService typeService;

    @Autowired
    protected TypeModuleProcessor(TypeService typeService) {
        this.typeService = typeService;
    }

    @Override
    public void process(TypeModule typeModule) {
        TypeDto dto = new TypeDto(typeModule);
        typeService.update(dto);
    }
}
