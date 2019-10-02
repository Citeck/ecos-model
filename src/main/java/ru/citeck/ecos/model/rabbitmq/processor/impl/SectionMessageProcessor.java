package ru.citeck.ecos.model.rabbitmq.processor.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.module.api.ModulePublishMsg;
import ru.citeck.ecos.apps.app.module.type.impl.section.SectionModule;
import ru.citeck.ecos.apps.spring.rabbitmq.exception.DataIsNotPresentedException;
import ru.citeck.ecos.model.dto.EcosSectionDto;
import ru.citeck.ecos.model.rabbitmq.processor.MessageProcessor;
import ru.citeck.ecos.model.service.EcosSectionService;
import ru.citeck.ecos.records2.RecordRef;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SectionMessageProcessor implements MessageProcessor {

    private EcosSectionService sectionService;
    private ObjectMapper mapper = new ObjectMapper();

    @Autowired
    protected SectionMessageProcessor(EcosSectionService sectionService) {
        this.sectionService = sectionService;
    }

    @Override
    public void process(ModulePublishMsg msg) throws IOException {
        byte[] formData = msg.getData();
        if (formData == null) {
            throw new DataIsNotPresentedException(SectionModule.TYPE);
        }

        ObjectNode node = (ObjectNode) mapper.readTree(msg.getData());
        EcosSectionDto dto = convertToDto(node);

        sectionService.update(dto);
    }

    private EcosSectionDto convertToDto(ObjectNode node) {
        String id = node.get("id").asText();
        String name = node.path("name").asText();
        String description = node.path("description").asText();
        String tenant = node.path("tenant").asText();
        JsonNode types = node.path("types");
        Set<RecordRef> typesRefs = null;
        if (types != null) {
            typesRefs = types.findValuesAsText("id").stream()
                .map(string -> RecordRef.create("type", string))
                .collect(Collectors.toSet());
        }
        return new EcosSectionDto(id, name, description, tenant, typesRefs);
    }
}
