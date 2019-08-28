package ru.citeck.ecos.model.record;

import ru.citeck.ecos.model.dto.EcosTypeDto;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;

public class EcosTypeRecord implements MetaValue {

    private final EcosTypeDto dto;

    public EcosTypeRecord(EcosTypeDto dto) {
        this.dto = dto;
    }

    @Override
    public Object getJson() {
        return dto;
    }

}
