package ru.citeck.ecos.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.citeck.ecos.apps.app.module.ModuleRef;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.objdata.ObjectData;
import ru.citeck.ecos.records2.scalar.MLText;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class TypeDto {

    private String id;
    private MLText name;
    private MLText description;
    private String tenant;
    private RecordRef parent;
    private String form;
    private Set<TypeAssociationDto> associations = new HashSet<>();
    private Set<ModuleRef> actions = new HashSet<>();
    private boolean inheritActions;
    private ObjectData attributes;
    private Set<TypeCreateVariantDto> createVariants = new HashSet<>();

    public TypeDto(TypeDto dto) {
        this.name = dto.name;
        this.description = dto.description;
        this.tenant = dto.tenant;
        this.parent = dto.parent;
        this.form = dto.form;
        this.id = dto.id;
        this.inheritActions = dto.inheritActions;
        this.attributes = dto.attributes;

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

        if (dto.createVariants != null) {
            this.createVariants = dto.createVariants;
        } else {
            this.createVariants = Collections.emptySet();
        }
    }
}
