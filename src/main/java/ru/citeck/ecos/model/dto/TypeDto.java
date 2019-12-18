package ru.citeck.ecos.model.dto;

import lombok.*;
import ru.citeck.ecos.apps.app.module.ModuleRef;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class TypeDto {

    private String id;
    private String name;
    private String description;
    private String tenant;
    private RecordRef parent;
    private Set<TypeAssociationDto> associations = new HashSet<>();
    private Set<ModuleRef> actions = new HashSet<>();
    private boolean inheritActions;

    public TypeDto(TypeDto dto) {
        this.name = dto.name;
        this.description = dto.description;
        this.tenant = dto.tenant;
        this.parent = dto.parent;
        this.id = dto.id;

        if (dto.associations != null) {
            this.associations = dto.associations;
        } else {
            this.associations = Collections.emptySet();
        }

        if (dto.actions != null) {
            this.actions = dto.actions;
        } else {
            this.actions = Collections.emptySet();
        }

        this.inheritActions = dto.isInheritActions();
    }
}
