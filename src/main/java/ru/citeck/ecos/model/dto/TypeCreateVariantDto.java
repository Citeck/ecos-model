package ru.citeck.ecos.model.dto;

import lombok.Data;

@Data
public class TypeCreateVariantDto {

    private String id;
    private String name;
    private String formRef;
    private String recordRef;
    private String attributes;
}
