package ru.citeck.ecos.model.eapps.listener;

import lombok.Data;
import ru.citeck.ecos.apps.module.ModuleRef;
import ru.citeck.ecos.commons.data.MLText;

@Data
public class AssociationDto {
    private String id;
    private MLText name;
    private ModuleRef target;
    private AssocDirection direction;
}
