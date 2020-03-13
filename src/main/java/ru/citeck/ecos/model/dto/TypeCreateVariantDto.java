package ru.citeck.ecos.model.dto;

import lombok.Data;
import ru.citeck.ecos.commons.data.ObjectData;

@Data
public class TypeCreateVariantDto {

    private String id;
    private String name;
    private String formRef;
    private String recordRef;
    private ObjectData attributes;
}
