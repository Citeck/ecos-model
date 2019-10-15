package ru.citeck.ecos.model.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.apps.EcosAppsApiFactory;
import ru.citeck.ecos.apps.app.module.type.section.SectionModule;
import ru.citeck.ecos.apps.app.module.type.type.TypeModule;
import ru.citeck.ecos.model.eapps.processor.ModuleProcessor;
import ru.citeck.ecos.model.eapps.processor.impl.SectionModuleProcessor;
import ru.citeck.ecos.model.eapps.processor.impl.TypeModuleProcessor;

import javax.annotation.PostConstruct;

@Slf4j
@Configuration
public class EcosModulesConfig {

    private ModuleProcessor<TypeModule> typeModuleProcessor;
    private ModuleProcessor<SectionModule> sectionModuleProcessor;
    private EcosAppsApiFactory apiFactory;

    @Autowired
    public EcosModulesConfig(TypeModuleProcessor typeMessageProcessor,
                             SectionModuleProcessor sectionMessageProcessor,
                             EcosAppsApiFactory apiFactory) {
        this.typeModuleProcessor = typeMessageProcessor;
        this.sectionModuleProcessor = sectionMessageProcessor;
        this.apiFactory = apiFactory;
    }

    @PostConstruct
    public void init() {
        apiFactory.getModuleApi().onModulePublished(TypeModule.class, typeModuleProcessor::process);
        apiFactory.getModuleApi().onModulePublished(SectionModule.class, sectionModuleProcessor::process);
    }

}
