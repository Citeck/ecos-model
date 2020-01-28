package ru.citeck.ecos.model.dto;

import lombok.Data;

import java.util.Map;

@Data
public class TypeCreateVariantDto {

    private String id;
    private String name;
    private String formRef;
    private String recordRef;
    private Map<String, Object> attributes;
}
