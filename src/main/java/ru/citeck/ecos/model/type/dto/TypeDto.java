package ru.citeck.ecos.model.type.dto;

import ecos.com.fasterxml.jackson210.annotation.JsonInclude;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.association.dto.AssociationDto;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt;
import ru.citeck.ecos.records2.type.ComputedAttribute;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonInclude(value = JsonInclude.Include.NON_EMPTY)
public class TypeDto {

    @NotNull
    private String id;
    private MLText name;
    private MLText description;
    private String tenant;
    private String sourceId;
    private RecordRef parentRef;
    private RecordRef formRef;
    private RecordRef journalRef;
    private boolean system;
    private String dashboardType;
    private boolean inheritActions;
    private boolean inheritForm;

    private MLText dispNameTemplate;

    private RecordRef numTemplateRef;
    private boolean inheritNumTemplate;

    private List<String> aliases = new ArrayList<>();

    private List<RecordRef> actions = new ArrayList<>();
    private List<AssociationDto> associations = new ArrayList<>();
    private List<CreateVariantDto> createVariants = new ArrayList<>();
    private List<ComputedAttribute> computedAttributes = new ArrayList<>();

    private ObjectData attributes = ObjectData.create();

    private RecordRef configFormRef;
    private ObjectData config = ObjectData.create();

    public TypeDto(TypeDto dto) {

        this.id = dto.id;
        this.sourceId = dto.sourceId;
        this.name = Json.getMapper().copy(dto.name);
        this.description = Json.getMapper().copy(dto.description);
        this.parentRef = dto.parentRef;
        this.formRef = dto.formRef;
        this.journalRef = dto.journalRef;
        this.system = dto.system;
        this.dashboardType = dto.dashboardType;
        this.inheritActions = dto.inheritActions;
        this.tenant = dto.tenant;
        this.configFormRef = dto.configFormRef;
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
        this.inheritForm = dto.isInheritForm();
    }

    public TypeDto() {
    }

    @MetaAtt("parentRef")
    public void setParent(RecordRef parentRef) {
        this.parentRef = parentRef;
    }

    @MetaAtt("formRef")
    public void setForm(RecordRef formRef) {
        this.formRef = formRef;
    }

    @MetaAtt("journalRef")
    public void setJournal(RecordRef journalRef) {
        this.journalRef = journalRef;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public MLText getName() {
        return name;
    }

    public void setName(MLText name) {
        this.name = name;
    }

    public MLText getDescription() {
        return description;
    }

    public void setDescription(MLText description) {
        this.description = description;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public RecordRef getParentRef() {
        return parentRef;
    }

    public void setParentRef(RecordRef parentRef) {
        this.parentRef = parentRef;
    }

    public RecordRef getFormRef() {
        return formRef;
    }

    public void setFormRef(RecordRef formRef) {
        this.formRef = formRef;
    }

    public RecordRef getJournalRef() {
        return journalRef;
    }

    public void setJournalRef(RecordRef journalRef) {
        this.journalRef = journalRef;
    }

    public boolean isSystem() {
        return system;
    }

    public void setSystem(boolean system) {
        this.system = system;
    }

    public String getDashboardType() {
        return dashboardType;
    }

    public void setDashboardType(String dashboardType) {
        this.dashboardType = dashboardType;
    }

    public boolean isInheritActions() {
        return inheritActions;
    }

    public void setInheritActions(boolean inheritActions) {
        this.inheritActions = inheritActions;
    }

    public boolean isInheritForm() {
        return inheritForm;
    }

    public void setInheritForm(boolean inheritForm) {
        this.inheritForm = inheritForm;
    }

    public MLText getDispNameTemplate() {
        return dispNameTemplate;
    }

    public void setDispNameTemplate(MLText dispNameTemplate) {
        this.dispNameTemplate = dispNameTemplate;
    }

    public RecordRef getNumTemplateRef() {
        return numTemplateRef;
    }

    public void setNumTemplateRef(RecordRef numTemplateRef) {
        this.numTemplateRef = numTemplateRef;
    }

    public boolean isInheritNumTemplate() {
        return inheritNumTemplate;
    }

    public void setInheritNumTemplate(boolean inheritNumTemplate) {
        this.inheritNumTemplate = inheritNumTemplate;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public void setAliases(List<String> aliases) {
        this.aliases = aliases;
    }

    public List<RecordRef> getActions() {
        return actions;
    }

    public void setActions(List<RecordRef> actions) {
        this.actions = actions;
    }

    public List<AssociationDto> getAssociations() {
        return associations;
    }

    public void setAssociations(List<AssociationDto> associations) {
        this.associations = associations;
    }

    public List<CreateVariantDto> getCreateVariants() {
        return createVariants;
    }

    public void setCreateVariants(List<CreateVariantDto> createVariants) {
        this.createVariants = createVariants;
    }

    public List<ComputedAttribute> getComputedAttributes() {
        return computedAttributes;
    }

    public void setComputedAttributes(List<ComputedAttribute> computedAttributes) {
        this.computedAttributes = computedAttributes;
    }

    public ObjectData getAttributes() {
        return attributes;
    }

    public void setAttributes(ObjectData attributes) {
        this.attributes = attributes;
    }

    public RecordRef getConfigFormRef() {
        return configFormRef;
    }

    public void setConfigFormRef(RecordRef configFormRef) {
        this.configFormRef = configFormRef;
    }

    public ObjectData getConfig() {
        return config;
    }

    public void setConfig(ObjectData config) {
        this.config = config;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TypeDto typeDto = (TypeDto) o;
        return system == typeDto.system &&
            inheritActions == typeDto.inheritActions &&
            inheritForm == typeDto.inheritForm &&
            inheritNumTemplate == typeDto.inheritNumTemplate &&
            Objects.equals(id, typeDto.id) &&
            Objects.equals(name, typeDto.name) &&
            Objects.equals(description, typeDto.description) &&
            Objects.equals(tenant, typeDto.tenant) &&
            Objects.equals(sourceId, typeDto.sourceId) &&
            Objects.equals(parentRef, typeDto.parentRef) &&
            Objects.equals(formRef, typeDto.formRef) &&
            Objects.equals(journalRef, typeDto.journalRef) &&
            Objects.equals(dashboardType, typeDto.dashboardType) &&
            Objects.equals(dispNameTemplate, typeDto.dispNameTemplate) &&
            Objects.equals(numTemplateRef, typeDto.numTemplateRef) &&
            Objects.equals(aliases, typeDto.aliases) &&
            Objects.equals(actions, typeDto.actions) &&
            Objects.equals(associations, typeDto.associations) &&
            Objects.equals(createVariants, typeDto.createVariants) &&
            Objects.equals(computedAttributes, typeDto.computedAttributes) &&
            Objects.equals(attributes, typeDto.attributes) &&
            Objects.equals(configFormRef, typeDto.configFormRef) &&
            Objects.equals(config, typeDto.config);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            id,
            name,
            description,
            tenant,
            sourceId,
            parentRef,
            formRef,
            journalRef,
            system,
            dashboardType,
            inheritActions,
            inheritForm,
            dispNameTemplate,
            numTemplateRef,
            inheritNumTemplate,
            aliases,
            actions,
            associations,
            createVariants,
            computedAttributes,
            attributes,
            configFormRef,
            config
        );
    }
}
