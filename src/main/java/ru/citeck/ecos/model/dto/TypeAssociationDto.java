package ru.citeck.ecos.model.dto;

import lombok.*;
import ru.citeck.ecos.records2.RecordRef;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class TypeAssociationDto {

    private String id;
    private String name;
    private RecordRef targetType;
}
