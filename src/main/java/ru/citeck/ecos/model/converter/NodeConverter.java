package ru.citeck.ecos.model.converter;

import ecos.com.fasterxml.jackson210.core.JsonProcessingException;
import ecos.com.fasterxml.jackson210.databind.JsonNode;
import ecos.com.fasterxml.jackson210.databind.ObjectMapper;
import ecos.com.fasterxml.jackson210.databind.node.NullNode;
import io.micrometer.core.instrument.util.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class NodeConverter implements Converter<String, JsonNode> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String targetToSource(JsonNode node) {
        try {
            return OBJECT_MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed write JsonNode as String", e);
        }
    }

    @Override
    public JsonNode sourceToTarget(String nodeData) {
        if (StringUtils.isBlank(nodeData)) {
            return NullNode.getInstance();
        }

        try {
            return OBJECT_MAPPER.readValue(nodeData, JsonNode.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed read JSON", e);
        }
    }

}
