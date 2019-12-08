package ru.citeck.ecos.model.eapps.processor.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.module.type.model.section.SectionModule;
import ru.citeck.ecos.model.dto.SectionDto;
import ru.citeck.ecos.model.eapps.processor.ModuleProcessor;
import ru.citeck.ecos.model.service.SectionService;

@Slf4j
@Component
public class SectionModuleProcessor implements ModuleProcessor<SectionModule> {

    private final SectionService sectionService;

    @Autowired
    protected SectionModuleProcessor(SectionService sectionService) {
        this.sectionService = sectionService;
    }

    @Override
    public void process(SectionModule module) {
        SectionDto dto = new SectionDto(module);
        sectionService.update(dto);
    }
}
