package ru.citeck.ecos.model.record.mutable;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.citeck.ecos.model.dto.EcosTypeDto;

import java.util.ArrayList;

@NoArgsConstructor
public class EcosTypeMutable extends EcosTypeDto {

    @Getter @Setter private String jsonContent;

    public EcosTypeMutable(EcosTypeDto dto) {
        super(dto);
    }

    public EcosTypeMutable(String extId) {
        super(extId, null, null, null, null, null, new ArrayList<>(),
            false);
    }
}
