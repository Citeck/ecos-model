package ru.citeck.ecos.model.rabbitmq.processor;

import ru.citeck.ecos.apps.app.module.api.ModulePublishMsg;

import java.io.IOException;

public interface MessageProcessor {

    void process(ModulePublishMsg msg) throws IOException;
}
