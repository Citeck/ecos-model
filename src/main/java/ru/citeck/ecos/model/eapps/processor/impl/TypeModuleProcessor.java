package ru.citeck.ecos.model.eapps.processor.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.module.type.type.TypeModule;
import ru.citeck.ecos.model.deploy.dto.EcosTypeDeployDto;
import ru.citeck.ecos.model.deploy.service.EcosTypeDeployService;
import ru.citeck.ecos.model.eapps.processor.ModuleProcessor;

@Component
@Slf4j
public class TypeModuleProcessor implements ModuleProcessor<TypeModule> {

    private EcosTypeDeployService typeDeployService;

    @Autowired
    protected TypeModuleProcessor(EcosTypeDeployService typeDeployService) {
        this.typeDeployService = typeDeployService;
    }

    @Override
    public void process(TypeModule typeModule) {
        EcosTypeDeployDto dto = new EcosTypeDeployDto(typeModule);
        typeDeployService.deploy(dto);
    }
}
