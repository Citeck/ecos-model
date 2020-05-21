package ru.citeck.ecos.model.association.dto;

import lombok.Data;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.records2.RecordRef;

@Data
public class AssociationDto {
    private String id;
    private MLText name;
    private String attribute;
    private RecordRef target;
    private AssocDirection direction;
}
