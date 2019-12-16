package ru.citeck.ecos.model.eapps.listener;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.module.type.model.section.SectionModule;
import ru.citeck.ecos.apps.spring.rabbit.EcosModuleListener;
import ru.citeck.ecos.model.converter.ModuleConverter;
import ru.citeck.ecos.model.converter.impl.module.SectionModuleConverter;
import ru.citeck.ecos.model.dto.SectionDto;
import ru.citeck.ecos.model.service.SectionService;

@Component
public class SectionModuleListener implements EcosModuleListener<SectionModule> {

    private final SectionService sectionService;
    private final ModuleConverter<SectionModule, SectionDto> sectionModuleConverter;

    @Autowired
    protected SectionModuleListener(SectionService sectionService,
                                    SectionModuleConverter sectionModuleConverter) {
        this.sectionService = sectionService;
        this.sectionModuleConverter = sectionModuleConverter;
    }

    @Override
    public void onModulePublished(SectionModule module) {
        SectionDto dto = sectionModuleConverter.moduleToDto(module);
        sectionService.update(dto);
    }

    @Override
    public void onModuleDeleted(String id) {
        sectionService.delete(id);
    }

    @Override
    public Class<SectionModule> getModuleType() {
        return SectionModule.class;
    }
}
