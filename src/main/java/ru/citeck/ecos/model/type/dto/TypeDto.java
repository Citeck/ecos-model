package ru.citeck.ecos.model.type.dto;

import ecos.com.fasterxml.jackson210.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.association.dto.AssociationDto;
import ru.citeck.ecos.records2.RecordRef;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(value = JsonInclude.Include.NON_EMPTY)
public class TypeDto {

    @NotNull
    private String id;
    private MLText name;
    private MLText description;
    private String tenant;
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

    private RecordRef configForm;
    private ObjectData config = new ObjectData();

    public TypeDto(TypeDto dto) {

        TypeDto copy = Json.getMapper().copy(dto);

        if (copy == null) {
            return;
        }

        this.id = dto.id;
        this.name = dto.name;
        this.description = dto.description;
        this.parent = dto.parent;
        this.form = dto.form;
        this.journal = dto.journal;
        this.system = dto.system;
        this.dashboardType = dto.dashboardType;
        this.inheritActions = dto.inheritActions;
        this.tenant = dto.tenant;
        this.configForm = dto.configForm;
        this.config = dto.config;
        this.aliases = copy.aliases;
        this.associations = copy.associations;
        this.actions = copy.actions;
        this.createVariants = copy.createVariants;
        this.attributes = copy.attributes;
    }
}
