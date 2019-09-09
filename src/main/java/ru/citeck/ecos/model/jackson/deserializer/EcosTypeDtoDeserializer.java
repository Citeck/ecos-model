package ru.citeck.ecos.model.jackson.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import ru.citeck.ecos.model.dto.EcosTypeDto;

import java.io.IOException;

public class EcosTypeDtoDeserializer extends JsonDeserializer<EcosTypeDto> {
    @Override
    public EcosTypeDto deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        JsonNode node = p.getCodec().readTree(p);
        String parentUuid = node.get("parent") != null ? node.get("parent").asText() : null;
        parentUuid = parentUuid != null ? parentUuid.substring(parentUuid.indexOf("@")+1) : "";
        String uuid = node.get("uuid") != null ? node.get("uuid").asText() : "";
        String name = node.get("name") != null ? node.get("name").asText() : null;
        String desc = node.get("description") != null ? node.get("description").asText() : null;
        String tenant = node.get("tenant") != null ? node.get("tenant").asText() : null;
        EcosTypeDto parent = new EcosTypeDto(parentUuid);
        return new EcosTypeDto(uuid, name, desc, tenant, null, null);
    }
}
