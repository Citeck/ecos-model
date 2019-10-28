package ru.citeck.ecos.model.dto;

import lombok.*;
import ru.citeck.ecos.apps.app.module.type.type.action.ActionDto;
import ru.citeck.ecos.records2.RecordRef;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Getter
@Setter
public class EcosTypeDto {

    private String id;
    private String name;
    private String description;
    private String tenant;
    private RecordRef parent;
    private Set<RecordRef> associations = new HashSet<>();
    private List<ActionDto> actions = new ArrayList<>();

    public EcosTypeDto(EcosTypeDto dto) {
        this.name = dto.name;
        this.description = dto.description;
        this.tenant = dto.tenant;
        this.parent = dto.parent;
        this.id = dto.id;
        if (dto.associations != null) {
            this.associations = new HashSet<>(dto.associations);
        }
        this.actions = dto.getActions();
    }

    public EcosTypeDto(String id) {
        this.id = id;
    }

}
