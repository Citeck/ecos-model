package ru.citeck.ecos.model.rabbitmq.processor.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.module.api.ModulePublishMsg;
import ru.citeck.ecos.apps.app.module.type.impl.type.TypeModule;
import ru.citeck.ecos.apps.spring.rabbitmq.exception.DataIsNotPresentedException;
import ru.citeck.ecos.model.deploy.dto.EcosTypeDeployDto;
import ru.citeck.ecos.model.deploy.service.EcosTypeDeployService;
import ru.citeck.ecos.model.dto.EcosAssociationDto;
import ru.citeck.ecos.model.rabbitmq.processor.MessageProcessor;
import ru.citeck.ecos.records2.RecordRef;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

@Component
@Slf4j
public class TypeMessageProcessor implements MessageProcessor {

    private EcosTypeDeployService typeDeployService;
    private ObjectMapper mapper = new ObjectMapper();

    @Autowired
    protected TypeMessageProcessor(EcosTypeDeployService typeDeployService) {
        this.typeDeployService = typeDeployService;
    }

    public void process(ModulePublishMsg msg) throws IOException {
        byte[] formData = msg.getData();
        if (formData == null) {
            throw new DataIsNotPresentedException(TypeModule.TYPE);
        }

        ObjectNode node = (ObjectNode) mapper.readTree(msg.getData());
        EcosTypeDeployDto dto = convertToDto(node);

        typeDeployService.deploy(dto);
    }

    private EcosTypeDeployDto convertToDto(ObjectNode node) {
        String id = node.get("id").asText();
        JsonNode parent = node.get("parent");
        RecordRef parentRef = null;
        if (parent != null && !parent.getNodeType().equals(JsonNodeType.NULL)) {
            String parentExtId = node.get("parent").get("id").asText();
            parentRef = RecordRef.create("type", parentExtId);
        }
        JsonNode assocNode = node.path("associations");
        Set<EcosAssociationDto> associationDtos = null;
        if (assocNode != null) {
            Iterator<JsonNode> assocIterator = assocNode.elements();
            associationDtos = new HashSet<>();
            while(assocIterator.hasNext()) {
                JsonNode assoc = assocIterator.next();

                JsonNode assocType = assoc.get("type");
                RecordRef targetRef = null;
                if (assocType != null && !assocType.getNodeType().equals(JsonNodeType.NULL)) {
                    targetRef = RecordRef.create("type", assocType.path("id").asText());
                }

                associationDtos.add(new EcosAssociationDto(
                    assoc.path("id").asText(),
                    assoc.path("name").asText(),
                    assoc.path("title").asText(),
                    RecordRef.create("type",id),
                    targetRef));
            }
        }
        return new EcosTypeDeployDto(
            id,
            node.path("name").asText(),
            node.path("description").asText(),
            node.path("tenant").asText(),
            parentRef,
            associationDtos);
    }
}
