package ru.citeck.ecos.model.deploy.dto;

import lombok.*;
import ru.citeck.ecos.model.dto.EcosAssociationDto;
import ru.citeck.ecos.records2.RecordRef;

import java.util.HashSet;
import java.util.Set;

@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class EcosTypeDeployDto {

    @Getter @Setter private String id;

    @Getter @Setter private String name;

    @Getter @Setter private String description;

    @Getter @Setter private String tenant;

    @Getter @Setter private RecordRef parent;

    @Getter @Setter private Set<EcosAssociationDto> associations;

    public EcosTypeDeployDto(EcosTypeDeployDto dto) {
        this.name = dto.name;
        this.description = dto.description;
        this.tenant = dto.tenant;
        this.parent = dto.parent;
        this.id = dto.id;
        if (dto.associations != null) {
            this.associations = new HashSet<>(dto.associations);
        }
    }

    public EcosTypeDeployDto(String id) {
        this.id = id;
    }

}
