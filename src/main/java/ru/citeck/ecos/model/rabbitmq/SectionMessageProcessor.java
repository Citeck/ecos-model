package ru.citeck.ecos.model.rabbitmq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.module.type.impl.section.SectionModule;
import ru.citeck.ecos.apps.queue.ModulePublishMsg;
import ru.citeck.ecos.apps.queue.ModulePublishResultMsg;
import ru.citeck.ecos.apps.spring.rabbitmq.exception.DataIsNotPresentedException;
import ru.citeck.ecos.apps.spring.rabbitmq.processor.MessageProcessor;
import ru.citeck.ecos.apps.spring.rabbitmq.sender.RabbitMQRespondent;
import ru.citeck.ecos.model.dto.EcosSectionDto;
import ru.citeck.ecos.model.service.EcosSectionService;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SectionMessageProcessor implements MessageProcessor<SectionModule> {

    private EcosSectionService sectionService;
    private ObjectMapper mapper = new ObjectMapper();
    private RabbitMQRespondent respondent;

    @Autowired
    protected SectionMessageProcessor(EcosSectionService sectionService, RabbitMQRespondent respondent) {
        this.sectionService = sectionService;
        this.respondent = respondent;
    }

    @Override
    public void process(ModulePublishMsg msg) {
        ModulePublishResultMsg result = new ModulePublishResultMsg();
        result.setRevId(msg.getRevId());
        try {
            byte[] formData = msg.getData();
            if (formData == null) {
                throw new DataIsNotPresentedException(SectionModule.TYPE);
            }

            ObjectNode node = (ObjectNode) mapper.readTree(msg.getData());
            EcosSectionDto dto = convertToDto(node);

            sectionService.update(dto);
            result.setSuccess(true);
        } catch (Exception e) {
            log.error("Cant process message", e);
            result.setMsg(e.getMessage());
            result.setSuccess(false);
        }
        respondent.reply(result);
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
