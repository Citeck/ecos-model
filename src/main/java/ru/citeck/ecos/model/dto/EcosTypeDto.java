package ru.citeck.ecos.model.dto;

import lombok.*;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Set;

@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class EcosTypeDto {

    @Getter @Setter private String extId;

    @Getter @Setter private String name;

    @Getter @Setter private String description;

    @Getter @Setter private String tenant;

    @Getter @Setter private RecordRef parent;

    @Getter @Setter private Set<RecordRef> sections;

    public EcosTypeDto(EcosTypeDto dto) {
        this.name = dto.name;
        this.description = dto.description;
        this.tenant = dto.tenant;
        this.parent = dto.parent;
        this.extId = dto.extId;
    }

    public EcosTypeDto(String extId) {
        this.extId = extId;
    }

}
