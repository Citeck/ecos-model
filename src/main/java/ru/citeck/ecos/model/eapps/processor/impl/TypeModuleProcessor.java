package ru.citeck.ecos.model.eapps.processor.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.module.type.type.TypeModule;
import ru.citeck.ecos.model.dto.EcosTypeDto;
import ru.citeck.ecos.model.eapps.processor.ModuleProcessor;
import ru.citeck.ecos.model.service.EcosTypeService;

@Component
@Slf4j
public class TypeModuleProcessor implements ModuleProcessor<TypeModule> {

    private EcosTypeService typeService;

    @Autowired
    protected TypeModuleProcessor(EcosTypeService typeService) {
        this.typeService = typeService;
    }

    @Override
    public void process(TypeModule typeModule) {
        EcosTypeDto dto = new EcosTypeDto(typeModule);
        typeService.update(dto);
    }
}
