package ru.citeck.ecos.model.deploy.dto;

import lombok.*;
import org.apache.logging.log4j.util.Strings;
import ru.citeck.ecos.apps.app.module.type.type.TypeModule;
import ru.citeck.ecos.model.dto.EcosAssociationDto;
import ru.citeck.ecos.records2.RecordRef;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

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

    public EcosTypeDeployDto(TypeModule module) {
        this.id = module.getId();
        this.name = module.getName();
        this.description = module.getDescription();
        this.tenant = Strings.EMPTY;
        if (!Strings.isEmpty(module.getParent())) {
            this.parent = RecordRef.create("type", module.getParent());
        }
        if (module.getAssociations() != null && !module.getAssociations().isEmpty()) {
            this.associations = module.getAssociations().stream()
                .map(a -> new EcosAssociationDto(a, module.getId()))
                .collect(Collectors.toSet());
        }
    }

    public EcosTypeDeployDto(String id) {
        this.id = id;
    }

}
