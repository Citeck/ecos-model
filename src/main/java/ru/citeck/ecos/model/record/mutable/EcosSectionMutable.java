package ru.citeck.ecos.model.record.mutable;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.citeck.ecos.model.dto.EcosSectionDto;

@NoArgsConstructor
public class EcosSectionMutable extends EcosSectionDto {

    @Getter @Setter private String jsonContent;

    public EcosSectionMutable(EcosSectionDto dto) {
        super(dto);
    }

    public EcosSectionMutable(String extId) {
        super(extId, null, null,null,null);
    }
}
