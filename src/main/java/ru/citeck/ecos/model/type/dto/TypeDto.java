package ru.citeck.ecos.model.type.dto;

import ecos.com.fasterxml.jackson210.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.association.dto.AssociationDto;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.type.ComputedAttribute;

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
    //todo: rename RecordRef fields to *Ref
    private RecordRef parent;
    private RecordRef form;
    private RecordRef journal;
    private boolean system;
    private String dashboardType;
    private boolean inheritActions;

    private MLText dispNameTemplate;

    private RecordRef numTemplateRef;
    private boolean inheritNumTemplate;

    private List<String> aliases = new ArrayList<>();

    private List<RecordRef> actions = new ArrayList<>();
    private List<AssociationDto> associations = new ArrayList<>();
    private List<CreateVariantDto> createVariants = new ArrayList<>();
    private List<ComputedAttribute> computedAttributes = new ArrayList<>();

    private ObjectData attributes = ObjectData.create();

    private RecordRef configForm;
    private ObjectData config = ObjectData.create();

    public TypeDto(TypeDto dto) {

        this.id = dto.id;
        this.sourceId = dto.sourceId;
        this.name = Json.getMapper().copy(dto.name);
        this.description = Json.getMapper().copy(dto.description);
        this.parent = dto.parent;
        this.form = dto.form;
        this.journal = dto.journal;
        this.system = dto.system;
        this.dashboardType = dto.dashboardType;
        this.inheritActions = dto.inheritActions;
        this.tenant = dto.tenant;
        this.configForm = dto.configForm;
        this.dispNameTemplate = Json.getMapper().copy(dto.getDispNameTemplate());
        this.inheritNumTemplate = dto.isInheritNumTemplate();
        this.config = ObjectData.deepCopy(dto.config);
        this.aliases = DataValue.create(dto.aliases).toList(String.class);
        this.associations = DataValue.create(dto.associations).toList(AssociationDto.class);
        this.actions = DataValue.create(dto.actions).toList(RecordRef.class);
        this.createVariants = DataValue.create(dto.createVariants).toList(CreateVariantDto.class);
        this.computedAttributes = DataValue.create(dto.computedAttributes).toList(ComputedAttribute.class);
        this.attributes = ObjectData.deepCopy(dto.attributes);
        this.numTemplateRef = dto.getNumTemplateRef();
    }
}
