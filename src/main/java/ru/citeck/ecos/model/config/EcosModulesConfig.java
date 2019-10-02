package ru.citeck.ecos.model.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.apps.EcosAppsApiFactory;
import ru.citeck.ecos.apps.app.module.type.impl.section.SectionModule;
import ru.citeck.ecos.apps.app.module.type.impl.type.TypeModule;
import ru.citeck.ecos.model.rabbitmq.processor.MessageProcessor;
import ru.citeck.ecos.model.rabbitmq.processor.impl.SectionMessageProcessor;
import ru.citeck.ecos.model.rabbitmq.processor.impl.TypeMessageProcessor;

import javax.annotation.PostConstruct;

@Slf4j
@Configuration
public class EcosModulesConfig {

    private MessageProcessor typeMessageProcessor;
    private MessageProcessor sectionMessageProcessor;
    private EcosAppsApiFactory apiFactory;

    @Autowired
    public EcosModulesConfig(TypeMessageProcessor typeMessageProcessor,
                             SectionMessageProcessor sectionMessageProcessor,
                             EcosAppsApiFactory apiFactory) {
        this.typeMessageProcessor = typeMessageProcessor;
        this.sectionMessageProcessor = sectionMessageProcessor;
        this.apiFactory = apiFactory;
    }

    @PostConstruct
    public void init() {
        apiFactory.getModuleApi().onModulePublished(TypeModule.TYPE, typeMessageProcessor::process);
        apiFactory.getModuleApi().onModulePublished(SectionModule.TYPE, sectionMessageProcessor::process);
    }

}
