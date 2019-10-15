package ru.citeck.ecos.model.eapps.processor.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.module.type.section.SectionModule;
import ru.citeck.ecos.model.dto.EcosSectionDto;
import ru.citeck.ecos.model.eapps.processor.ModuleProcessor;
import ru.citeck.ecos.model.service.EcosSectionService;

@Slf4j
@Component
public class SectionModuleProcessor implements ModuleProcessor<SectionModule> {

    private EcosSectionService sectionService;

    @Autowired
    protected SectionModuleProcessor(EcosSectionService sectionService) {
        this.sectionService = sectionService;
    }

    @Override
    public void process(SectionModule module) {
        EcosSectionDto dto = new EcosSectionDto(module);
        sectionService.update(dto);
    }
}
