package ru.citeck.ecos.model.type.records.dao;

import ecos.com.fasterxml.jackson210.annotation.JsonIgnore;
import ecos.com.fasterxml.jackson210.annotation.JsonProperty;
import ecos.com.fasterxml.jackson210.annotation.JsonValue;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.commons.json.YamlUtils;
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef;
import ru.citeck.ecos.model.lib.role.dto.RoleDef;
import ru.citeck.ecos.model.lib.status.dto.StatusDef;
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef;
import ru.citeck.ecos.model.lib.type.service.TypeDefService;
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils;
import ru.citeck.ecos.model.section.records.record.SectionRecord;
import ru.citeck.ecos.model.type.dto.TypeDef;
import ru.citeck.ecos.model.type.dto.TypeWithMetaDto;
import ru.citeck.ecos.model.type.service.TypeService;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt;
import ru.citeck.ecos.records2.graphql.meta.value.EmptyValue;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.records2.request.delete.RecordsDelResult;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.MutableRecordsLocalDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDao;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class TypeRecordsDaoOld extends LocalRecordsDao
                            implements LocalRecordsQueryWithMetaDao<TypeRecordsDaoOld.TypeRecord>,
                                       LocalRecordsMetaDao<MetaValue>,
                                       MutableRecordsLocalDao<TypeRecordsDaoOld.TypeMutRecord> {

    public static final String ID = "type";
    public static final String LANG_EXPAND_TYPES = "expand-types";

    private static final String TYPE_ACTIONS_WITH_INHERIT_ATT_JSON = "_actions[]?id";

    private final TypeRecord EMPTY_RECORD = new TypeRecord(new TypeWithMetaDto());

    private final TypeService typeService;
    private final TypeDefService typeDefService;

    @Autowired
    public TypeRecordsDaoOld(TypeService typeService, TypeDefService typeDefService) {
        setId(ID);
        this.typeService = typeService;
        this.typeDefService = typeDefService;
    }

    @Override
    public List<MetaValue> getLocalRecordsMeta(List<RecordRef> list, MetaField metaField) {

        if (list.size() == 1 && list.get(0).getId().isEmpty()) {
            return Collections.singletonList(EMPTY_RECORD);
        }

        return list.stream()
            .map(ref -> {
                TypeWithMetaDto type = typeService.getByIdOrNull(ref.getId());
                return type != null ? new TypeRecord(type) : EmptyValue.INSTANCE;
            })
            .collect(Collectors.toList());
    }

    @Override
    public RecordsQueryResult<TypeRecord> queryLocalRecords(RecordsQuery recordsQuery, MetaField metaField) {


    }

    @Override
    public RecordsDelResult delete(RecordsDeletion recordsDeletion) {

        List<RecordMeta> result = new ArrayList<>();

        recordsDeletion.getRecords().forEach(r -> {
            typeService.delete(r.getId());
            result.add(new RecordMeta(r));
        });

        RecordsDelResult delRes = new RecordsDelResult();
        delRes.setRecords(result);

        return delRes;
    }

    @Override
    public List<TypeMutRecord> getValuesToMutate(List<RecordRef> list) {
        return list.stream()
            .map(RecordRef::getId)
            .map(id -> {
                if (StringUtils.isBlank(id)) {
                    return new TypeMutRecord();
                } else {
                    return new TypeMutRecord(typeService.getById(id));
                }
            })
            .collect(Collectors.toList());
    }

    @Override
    public RecordsMutResult save(List<TypeMutRecord> list) {

        RecordsMutResult result = new RecordsMutResult();

        list.forEach(sec -> {
            TypeDto resDto = typeService.save(sec);
            result.addRecord(new RecordMeta(RecordRef.valueOf(resDto.getId())));
        });

        return result;
    }

    /**
     * This class must be like {@link SectionRecord} in dedicated '.class' file,
     * but we don't need to set inherited type actions in dto or record.
     */
    public class TypeRecord implements MetaValue {

        private final TypeWithMetaDto dto;
        private final boolean innerType;

        public TypeRecord(TypeWithMetaDto dto) {
            this(dto, false);
        }

        public TypeRecord(TypeWithMetaDto dto, boolean innerType) {
            this.dto = dto;
            this.innerType = innerType;
        }

        @Override
        public String getId() {
            return innerType ? "emodel/type@" + dto.getId() : dto.getId();
        }

        @Override
        public String getDisplayName() {
            MLText name = dto.getName();
            String dispName;
            if (name == null) {
                dispName = dto.getId();
            } else {
                //todo: should be user locale
                dispName = name.getClosestValue(Locale.ENGLISH);
            }
            return dispName;
        }

        @Override
        public Object getAttribute(String name, MetaField field) {
            switch (name) {
                case "name":
                    return dto.getName();
                case "extId":
                case "moduleId":
                    return dto.getId();
                case "description":
                    return dto.getDescription();
                case "inheritActions":
                    return dto.isInheritActions();
                case "parent":
                case "parentRef":
                case RecordConstants.ATT_PARENT:
                    return dto.getParentRef();
                case "parents":
                    return toInnerTypeRecords(typeService.getParentIds(dto.getId()));
                case "children":
                    return toInnerTypeRecords(typeService.getChildren(dto.getId()));
                case "actions":
                    return dto.getActions();
                case RecordConstants.ATT_ACTIONS:
                    return getInheritTypeActions(dto);
                case "associations":
                    return dto.getAssociations();
                case "assocsFull":
                    return typeService.getFullAssocs(dto.getId());
                case "form":
                case "formRef":
                    return dto.getFormRef();
                case "inhFormRef":
                    if (RecordRef.isNotEmpty(dto.getFormRef()) || !dto.isInheritForm()) {
                        return dto.getFormRef();
                    }
                    return typeService.getInhFormRef(dto.getId());
                case "inheritForm":
                    return dto.isInheritForm();
                case "journal":
                case "journalRef":
                    return dto.getJournalRef();
                case "inheritedForm":
                    return findAndGetInheritedForm(dto);
                case "attributes":
                case "properties":
                    return dto.getProperties();
                case "defaultCreateVariant":
                    return dto.getDefaultCreateVariant();
                case "createVariants":
                    return dto.getCreateVariants();
                case "postCreateActionRef":
                    return dto.getPostCreateActionRef();
                case "dashboardType":
                    return dto.getDashboardType();
                case "inhDashboardType":
                    return typeService.getDashboardType(dto.getId());
                case "inhCreateVariants":
                    return typeService.getCreateVariants(dto.getId());
                case "isSystem":
                    return dto.isSystem();
                case RecordConstants.ATT_FORM_KEY:
                    return "module_model/type";
                case "config":
                    return dto.getConfig();
                case "configFormRef":
                    return dto.getConfigFormRef();
                case "inhConfigFormRef":
                    return typeService.getConfigFormRef(dto.getId());
                case "inhAttributes":
                    return new InhAttributes(dto.getId());
                case RecordConstants.ATT_MODIFIED:
                    return dto.getModified();
                case RecordConstants.ATT_MODIFIER:
                    return dto.getModifier(); //todo: return RecordRef of User
                case RecordConstants.ATT_CREATED:
                    return dto.getCreated();
                case RecordConstants.ATT_CREATOR:
                    return dto.getCreator();
                case "sourceId":
                    return dto.getSourceId();
                case "inhSourceId":
                    return typeService.getInhSourceId(dto.getId());
                case "dispNameTemplate":
                    return dto.getDispNameTemplate();
                case "inheritNumTemplate":
                    return dto.isInheritNumTemplate();
                case "numTemplateRef":
                    return dto.getNumTemplateRef();
                case "model":
                    return dto.getModel();
                case "resolvedModel":
                    return typeDefService.getModelDef(TypeUtils.getTypeRef(dto.getId()));
                case "modelRoles":
                    return dto.getModel().getRoles();
                case "modelStatuses":
                    return dto.getModel().getStatuses();
                case "modelAttributes":
                    return dto.getModel().getAttributes();
                case "docLibEnabled":
                    return dto.getDocLib().getEnabled();
                case "docLibFileTypeRefs":
                    return dto.getDocLib().getFileTypeRefs();
                case "docLibDirTypeRef":
                    return dto.getDocLib().getDirTypeRef();
                case "docLib":
                    return dto.getDocLib();
                case "resolvedDocLib":
                    return typeDefService.getDocLib(TypeUtils.getTypeRef(dto.getId()));
                case "metaRecord":
                    return dto.getMetaRecord();
                case "data":
                    return YamlUtils.toNonDefaultString(getJson()).getBytes(StandardCharsets.UTF_8);
            }
            return null;
        }

        @Override
        public Object getJson() {
            return Json.getMapper().toNonDefaultJson(new TypeDto(dto));
        }

        @Override
        public RecordRef getRecordType() {
            return RecordRef.create("emodel", "type", "type");
        }
    }

    private List<TypeRecord> toInnerTypeRecords(Collection<TypeWithMetaDto> types) {
        return types.stream()
            .map(dto -> new TypeRecord(dto, true))
            .collect(Collectors.toList());
    }

    private RecordRef findAndGetInheritedForm(TypeDto typeDto) {

        TypeDto currentType = typeDto;

        while (currentType != null) {

            RecordRef formId = currentType.getFormRef();

            if (formId != null) {
                return formId;
            } else {
                if (currentType.getParentRef() != null) {
                    currentType = typeService.getById(currentType.getParentRef().getId());
                } else {
                    return null;
                }
            }
        }

        return null;
    }

    @Data
    public static class TypesByJournalListQuery {
        private String listId;
    }

    @RequiredArgsConstructor
    public class InhAttributes implements MetaValue {

        private final String extId;

        @Override
        public Object getAttribute(String name, MetaField field) {
            return typeService.getInhAttribute(extId, name);
        }
    }

    public static class TypeMutRecord extends TypeDto {

        TypeMutRecord() {
        }

        TypeMutRecord(TypeDef dto) {
            super(dto);
            if (getModel() == null) {
                setModel(TypeModelDef.EMPTY);
            }
        }

        public String getModuleId() {
            return getId();
        }

        public void setModuleId(String value) {
            setId(value);
        }

        public void setModelRoles(List<RoleDef> roles) {
            setModel(getModel().copy().withRoles(roles).build());
        }

        public void setModelStatuses(List<StatusDef> statuses) {
            setModel(getModel().copy().withStatuses(statuses).build());
        }

        public void setModelAttributes(List<AttributeDef> attributes) {
            setModel(getModel().copy().withAttributes(attributes).build());
        }

        public void setDocLibEnabled(boolean enabled) {
            setDocLib(getDocLib().copy().withEnabled(enabled).build());
        }

        public void setDocLibFileTypeRefs(List<RecordRef> fileTypeRefs) {
            setDocLib(getDocLib().copy().withFileTypeRefs(fileTypeRefs).build());
        }

        public void setDocLibDirTypeRef(RecordRef typeRef) {
            setDocLib(getDocLib().copy().withDirTypeRef(typeRef).build());
        }

        @JsonIgnore
        @MetaAtt(".disp")
        public String getDisplayName() {
            String result = getId();
            return result != null ? result : "Section";
        }

        @JsonProperty("_content")
        public void setContent(List<ObjectData> content) {

            String dataUriContent = content.get(0).get("url", "");
            ObjectData data = Json.getMapper().read(dataUriContent, ObjectData.class);

            Json.getMapper().applyData(this, data);
        }

        @JsonValue
        public Object toJson() {
            return Json.getMapper().toNonDefaultJson(new TypeDef(this));
        }

        public byte[] getData() {
            return YamlUtils.toNonDefaultString(toJson()).getBytes(StandardCharsets.UTF_8);
        }
    }

    @Data
    public static class ExpandTypesQuery {
        private List<RecordRef> typeRefs;
    }
}
