package ru.citeck.ecos.model.type.dto;

import org.apache.commons.lang.StringUtils;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.association.dto.AssociationDto;
import ru.citeck.ecos.model.lib.type.dto.CreateVariantDef;
import ru.citeck.ecos.model.lib.type.dto.DocLibDef;
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.record.op.atts.service.schema.annotation.AttName;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TypeDto {

    @NotNull
    private String id;
    private MLText name = MLText.EMPTY;
    private MLText description = MLText.EMPTY;
    private String sourceId = "";
    private RecordRef metaRecord = RecordRef.EMPTY;
    private RecordRef parentRef = RecordRef.EMPTY;
    private RecordRef formRef = RecordRef.EMPTY;
    private RecordRef journalRef = RecordRef.EMPTY;
    private boolean system;
    private String dashboardType = "";
    private boolean inheritActions;
    private boolean inheritForm;

    private MLText dispNameTemplate = MLText.EMPTY;

    private RecordRef numTemplateRef = RecordRef.EMPTY;
    private boolean inheritNumTemplate;

    private List<String> aliases = new ArrayList<>();

    private List<RecordRef> actions = new ArrayList<>();
    private List<AssociationDto> associations = new ArrayList<>();

    private RecordRef postCreateActionRef = RecordRef.EMPTY;

    private Boolean defaultCreateVariant;
    private List<CreateVariantDef> createVariants = new ArrayList<>();

    private ObjectData attributes = ObjectData.create();

    private RecordRef configFormRef = RecordRef.EMPTY;
    private ObjectData config = ObjectData.create();

    @AttName("model?json")
    private TypeModelDef model = TypeModelDef.EMPTY;

    @AttName("docLib?json")
    private DocLibDef docLib = DocLibDef.EMPTY;

    public TypeDto(TypeDto dto) {

        this.id = dto.id;
        this.sourceId = dto.sourceId;
        this.metaRecord = dto.metaRecord;
        this.name = Json.getMapper().copy(dto.name);
        this.description = Json.getMapper().copy(dto.description);
        this.parentRef = dto.parentRef;
        this.formRef = dto.formRef;
        this.journalRef = dto.journalRef;
        this.system = dto.system;
        this.dashboardType = dto.dashboardType;
        this.inheritActions = dto.inheritActions;
        this.configFormRef = dto.configFormRef;
        this.dispNameTemplate = Json.getMapper().copy(dto.getDispNameTemplate());
        this.inheritNumTemplate = dto.isInheritNumTemplate();
        this.config = ObjectData.deepCopy(dto.config);
        this.aliases = DataValue.create(dto.aliases).toList(String.class);
        this.associations = DataValue.create(dto.associations).toList(AssociationDto.class);
        this.actions = DataValue.create(dto.actions).toList(RecordRef.class);
        this.postCreateActionRef = dto.postCreateActionRef;
        this.defaultCreateVariant = dto.defaultCreateVariant;
        this.createVariants = DataValue.create(dto.createVariants).toList(CreateVariantDef.class);
        this.attributes = ObjectData.deepCopy(dto.attributes);
        this.numTemplateRef = dto.getNumTemplateRef();
        this.inheritForm = dto.isInheritForm();
        this.model = dto.getModel() != null ? dto.getModel().copy().build() : null;
        this.docLib = dto.getDocLib() != null ? dto.getDocLib().copy().build() : null;
    }

    public TypeDto() {
    }

    public TypeModelDef getModel() {
        return model;
    }

    public void setModel(TypeModelDef model) {
        this.model = model;
    }

    public DocLibDef getDocLib() {
        return docLib;
    }

    public void setDocLib(DocLibDef docLib) {
        this.docLib = docLib;
    }

    @AttName("parentRef")
    public void setParent(RecordRef parentRef) {
        this.parentRef = parentRef;
    }

    @AttName("formRef")
    public void setForm(RecordRef formRef) {
        this.formRef = formRef;
    }

    @AttName("journalRef")
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
        if (this.name == null) {
            this.name = MLText.EMPTY;
        }
    }

    public MLText getDescription() {
        return description;
    }

    public void setDescription(MLText description) {
        this.description = description;
        if (this.description == null) {
            this.description = MLText.EMPTY;
        }
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = StringUtils.defaultIfBlank(sourceId, "");
    }

    public RecordRef getMetaRecord() {
        return metaRecord;
    }

    public void setMetaRecord(RecordRef metaRecord) {
        this.metaRecord = RecordRef.valueOf(metaRecord);
    }

    public RecordRef getParentRef() {
        return parentRef;
    }

    public void setParentRef(RecordRef parentRef) {
        this.parentRef = RecordRef.valueOf(parentRef);
    }

    public RecordRef getFormRef() {
        return formRef;
    }

    public void setFormRef(RecordRef formRef) {
        this.formRef = RecordRef.valueOf(formRef);
    }

    public RecordRef getJournalRef() {
        return journalRef;
    }

    public void setJournalRef(RecordRef journalRef) {
        this.journalRef = RecordRef.valueOf(journalRef);
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
        this.dashboardType = StringUtils.defaultIfBlank(dashboardType, "");
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
        this.dispNameTemplate = dispNameTemplate != null ? dispNameTemplate : MLText.EMPTY;
    }

    public RecordRef getNumTemplateRef() {
        return numTemplateRef;
    }

    public void setNumTemplateRef(RecordRef numTemplateRef) {
        this.numTemplateRef = RecordRef.valueOf(numTemplateRef);
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

    public List<CreateVariantDef> getCreateVariants() {
        return createVariants;
    }

    public void setCreateVariants(List<CreateVariantDef> createVariants) {
        this.createVariants = createVariants;
    }

    public RecordRef getPostCreateActionRef() {
        return postCreateActionRef;
    }

    public void setPostCreateActionRef(RecordRef postCreateActionRef) {
        this.postCreateActionRef = postCreateActionRef;
    }

    public Boolean getDefaultCreateVariant() {
        return defaultCreateVariant;
    }

    public void setDefaultCreateVariant(Boolean defaultCreateVariant) {
        this.defaultCreateVariant = defaultCreateVariant;
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
        this.configFormRef = RecordRef.valueOf(configFormRef);
    }

    public ObjectData getConfig() {
        return config;
    }

    public void setConfig(ObjectData config) {
        this.config = config;
        if (this.config == null) {
            this.config = ObjectData.create();
        }
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
            Objects.equals(defaultCreateVariant, typeDto.defaultCreateVariant) &&
            Objects.equals(createVariants, typeDto.createVariants) &&
            Objects.equals(postCreateActionRef, typeDto.postCreateActionRef) &&
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
            defaultCreateVariant,
            createVariants,
            postCreateActionRef,
            attributes,
            configFormRef,
            config
        );
    }
}
