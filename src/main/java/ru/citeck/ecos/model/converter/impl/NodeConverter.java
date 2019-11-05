package ru.citeck.ecos.model.converter.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import io.micrometer.core.instrument.util.StringUtils;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.converter.Converter;

import java.io.IOException;

@Component
public class NodeConverter implements Converter<String, JsonNode> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public String targetToSource(JsonNode node) {
        try {
            return OBJECT_MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed write JsonNode as String", e);
        }
    }

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
