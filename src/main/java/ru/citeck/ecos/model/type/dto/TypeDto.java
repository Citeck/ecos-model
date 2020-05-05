package ru.citeck.ecos.model.type.dto;

import ecos.com.fasterxml.jackson210.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.model.association.dto.AssociationDto;
import ru.citeck.ecos.records2.RecordRef;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(value = JsonInclude.Include.NON_EMPTY)
public class TypeDto {

    private String id;
    private MLText name;
    private MLText description;
    private String sourceId;
    private RecordRef parent;
    private RecordRef form;
    private RecordRef journal;
    private boolean system;
    private String dashboardType;
    private boolean inheritActions;

    private List<String> aliases = new ArrayList<>();

    private List<RecordRef> actions = new ArrayList<>();
    private List<AssociationDto> associations = new ArrayList<>();
    private List<CreateVariantDto> createVariants = new ArrayList<>();

    private ObjectData attributes = new ObjectData();

    public TypeDto(TypeDto dto) {

        this.id = dto.id;
        this.name = dto.name;
        this.sourceId = dto.sourceId;
        this.description = dto.description;
        this.parent = dto.parent;
        this.form = dto.form;
        this.journal = dto.journal;
        this.system = dto.system;
        this.dashboardType = dto.dashboardType;
        this.inheritActions = dto.inheritActions;

        if (dto.aliases != null) {
            this.aliases = new ArrayList<>(dto.aliases);
        }
        if (dto.associations != null) {
            this.associations = new ArrayList<>(dto.associations);
        }
        if (dto.actions != null) {
            this.actions = new ArrayList<>(dto.actions);
        }
        if (dto.createVariants != null) {
            this.createVariants = new ArrayList<>(dto.createVariants);
        }

        this.attributes = ObjectData.deepCopy(dto.attributes);
    }
}
