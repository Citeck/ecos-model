package ru.citeck.ecos.model.dto;

import lombok.Data;
import ru.citeck.ecos.records2.objdata.ObjectData;

@Data
public class TypeCreateVariantDto {

    private String id;
    private String name;
    private String formRef;
    private String recordRef;
    private ObjectData attributes;
}
