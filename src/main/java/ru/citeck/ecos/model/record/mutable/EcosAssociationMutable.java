package ru.citeck.ecos.model.record.mutable;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.citeck.ecos.model.dto.EcosAssociationDto;

@NoArgsConstructor
public class EcosAssociationMutable extends EcosAssociationDto {

    @Getter @Setter private String jsonContent;

    public EcosAssociationMutable(EcosAssociationDto dto) {
        super(dto);
    }

    public EcosAssociationMutable(String id) {
        super(id, null, null,null, null);
    }
}
