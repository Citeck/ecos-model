package ru.citeck.ecos.model.converter.dto.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.converter.dto.AbstractDtoConverter;
import ru.citeck.ecos.model.dto.TypeCreateVariantDto;

import java.io.IOException;

@Component
@Slf4j
public class TypeCreateVariantConverter extends AbstractDtoConverter<TypeCreateVariantDto, String> {

    private final ObjectMapper objectMapper;

    public TypeCreateVariantConverter() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String dtoToEntity(TypeCreateVariantDto typeCreateVariantDto) {
        try {
            return objectMapper.writeValueAsString(typeCreateVariantDto);
        } catch (JsonProcessingException jpe) {
            log.error("Cannot serialize typeCreateVariantDto value with id: " + typeCreateVariantDto.getId());
        }
        return null;
    }

    @Override
    public TypeCreateVariantDto entityToDto(String content) {
        try {
            return objectMapper.readValue(content, TypeCreateVariantDto.class);
        } catch (IOException io) {
            log.error("Cannot deserialize content of string: " + content);
        }
        return null;
    }
}
