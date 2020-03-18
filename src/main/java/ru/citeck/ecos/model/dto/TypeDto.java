package ru.citeck.ecos.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.model.eapps.listener.AssociationDto;
import ru.citeck.ecos.model.eapps.listener.CreateVariantDto;
import ru.citeck.ecos.records2.RecordRef;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class TypeDto {

    private String id;
    private MLText name;
    private MLText description;
    private RecordRef parent;
    private RecordRef form;
    private boolean system;
    private String dashboardType;
    private boolean inheritActions;

    private List<RecordRef> actions = new ArrayList<>();
    private List<AssociationDto> associations = new ArrayList<>();
    private List<CreateVariantDto> createVariants = new ArrayList<>();

    private ObjectData attributes = new ObjectData();

    public TypeDto(TypeDto dto) {

        this.name = dto.name;
        this.description = dto.description;
        this.parent = dto.parent;
        this.form = dto.form;
        this.id = dto.id;
        this.inheritActions = dto.inheritActions;
        this.attributes = ObjectData.deepCopy(dto.attributes);
        this.system = dto.system;
        this.dashboardType = dto.dashboardType;

        if (dto.associations != null) {
            this.associations = new ArrayList<>(dto.associations);
        }
        if (dto.actions != null) {
            this.actions = new ArrayList<>(dto.actions);
        }
        if (dto.createVariants != null) {
            this.createVariants = new ArrayList<>(dto.createVariants);
        }
    }
}
