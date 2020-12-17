package ru.citeck.ecos.model.type.records.dao;

import ecos.com.fasterxml.jackson210.annotation.JsonIgnore;
import ecos.com.fasterxml.jackson210.annotation.JsonProperty;
import ecos.com.fasterxml.jackson210.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.section.records.record.SectionRecord;
import ru.citeck.ecos.model.type.dto.TypeDto;
import ru.citeck.ecos.model.type.dto.TypeWithMetaDto;
import ru.citeck.ecos.model.type.service.TypeService;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt;
import ru.citeck.ecos.records2.graphql.meta.value.EmptyValue;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records2.request.delete.RecordsDelResult;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.MutableRecordsLocalDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDao;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class TypeRecordsDao extends LocalRecordsDao
                            implements LocalRecordsQueryWithMetaDao<TypeRecordsDao.TypeRecord>,
                                       LocalRecordsMetaDao<MetaValue>,
                                       MutableRecordsLocalDao<TypeRecordsDao.TypeMutRecord> {

    public static final String ID = "type";

    private static final String TYPE_ACTIONS_WITH_INHERIT_ATT_JSON = "_actions[]?id";

    private final TypeRecord EMPTY_RECORD = new TypeRecord(new TypeWithMetaDto());

    private final TypeService typeService;
    private final RecordsService recordsService;

    @Autowired
    public TypeRecordsDao(TypeService typeService,
                          @Lazy RecordsService recordsService) {
        setId(ID);
        this.typeService = typeService;
        this.recordsService = recordsService;
    }

    @Override
    public List<MetaValue> getLocalRecordsMeta(List<RecordRef> list, MetaField metaField) {

        if (list.size() == 1 && list.get(0).getId().isEmpty()) {
            return Collections.singletonList(EMPTY_RECORD);
        }

        return list.stream()
            .map(ref -> {
                TypeWithMetaDto type = typeService.getByExtIdOrNull(ref.getId());
                return type != null ? new TypeRecord(type) : EmptyValue.INSTANCE;
            })
            .collect(Collectors.toList());
    }

    @Override
    public RecordsQueryResult<TypeRecord> queryLocalRecords(RecordsQuery recordsQuery, MetaField metaField) {

        RecordsQueryResult<TypeRecord> result = new RecordsQueryResult<>();

        if (recordsQuery.getLanguage().equals(PredicateService.LANGUAGE_PREDICATE)) {

            Predicate predicate = recordsQuery.getQuery(Predicate.class);

            int max = recordsQuery.getMaxItems();
            if (max <= 0) {
                max = 10000;
            }

            List<Sort.Order> order = recordsQuery.getSortBy()
                .stream()
                .map(s -> {
                    String attribute = s.getAttribute();
                    if (RecordConstants.ATT_MODIFIED.equals(attribute)) {
                        attribute = "lastModifiedDate";
                    } else {
                        return Optional.<Sort.Order>empty();
                    }
                    return Optional.of(s.isAscending() ? Sort.Order.asc(attribute) : Sort.Order.desc(attribute));
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

            Collection<TypeWithMetaDto> types = typeService.getAll(
                max,
                recordsQuery.getSkipCount(),
                predicate,
                !order.isEmpty() ? Sort.by(order) : null
            );

            result.setRecords(types.stream()
                .map(TypeRecord::new)
                .collect(Collectors.toList()));
            result.setTotalCount(typeService.getCount(predicate));

        } else {

            int max = recordsQuery.getMaxItems();
            Collection<TypeWithMetaDto> types;
            if (max < 0) {
                types = typeService.getAll();
            } else {
                types = typeService.getAll(max, recordsQuery.getSkipCount());
            }
            result.setRecords(types.stream()
                .map(TypeRecord::new)
                .collect(Collectors.toList()));
            result.setTotalCount(typeService.getCount());
        }
        return result;
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
                    return new TypeMutRecord(typeService.getByExtId(id));
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
                    return toInnerTypeRecords(typeService.getParents(dto.getId()));
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
                    return dto.getAttributes();
                case "createVariants":
                    return dto.getCreateVariants();
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
                case "dispNameTemplate":
                    return dto.getDispNameTemplate();
                case "inheritNumTemplate":
                    return dto.isInheritNumTemplate();
                case "computedAttributes":
                    return dto.getComputedAttributes();
                case "numTemplateRef":
                    return dto.getNumTemplateRef();
                case "docPermRef":
                    //todo
                    return RecordRef.create("emodel", "docperm", "perm-config-123");
                case "roles":
                    return Arrays.asList(
                        new RoleInfo("initiator",
                            Json.getMapper().convert("{\"ru\":\"Инициатор\",\"en\":\"Initiator\"}", MLText.class)),
                        new RoleInfo("approver",
                            Json.getMapper().convert("{\"ru\":\"Согласующий\",\"en\":\"Approver\"}", MLText.class)),
                        new RoleInfo("technologist",
                            Json.getMapper().convert("{\"ru\":\"Технолог\",\"en\":\"Technologist\"}", MLText.class))
                    );
                case "resolvedModel":
                    //temp solution for ECOS 3.*
                    DataValue value = dto.getAttributes().get("resolvedModel");
                    if (value.isTextual()) {
                        value = DataValue.create(value.asText());
                    }
                    if (!value.get("attributes").isArray()) {
                        value.set("attributes", DataValue.createArr());
                    }
                    return value.asJavaObj();
            }
            return null;
        }

        @Override
        public Object getJson() {
            return dto;
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
                    currentType = typeService.getByExtId(currentType.getParentRef().getId());
                } else {
                    return null;
                }
            }
        }

        return null;
    }

    private List<RecordRef> getInheritTypeActions(TypeDto dto) {

        if (!dto.isInheritActions() || dto.getParentRef() == null) {
            return dto.getActions();
        }

        RecordRef parent = dto.getParentRef();
        DataValue actionsNode = recordsService.getAttribute(parent, TYPE_ACTIONS_WITH_INHERIT_ATT_JSON);

        if (actionsNode == null || actionsNode.isNull()) {
            return dto.getActions();
        }

        Map<String, RecordRef> actionDtoMap = dto.getActions()
            .stream()
            .collect(Collectors.toMap(RecordRef::getId, Function.identity()));

        if (actionsNode.isArray()) {
            RecordRef[] actionsFromParent;
            try {
                actionsFromParent = Json.getMapper().convert(actionsNode, RecordRef[].class);
                if (actionsFromParent != null) {
                    for (RecordRef actionDto : actionsFromParent) {
                        actionDtoMap.putIfAbsent(actionDto.getId(), actionDto);
                    }
                }
            } catch (RuntimeException e) {
                throw new RuntimeException("Can not parse actions from parent", e);
            }
        } else {
            try {
                RecordRef actionFromParent = Json.getMapper().convert(actionsNode, RecordRef.class);
                if (actionFromParent != null) {
                    actionDtoMap.putIfAbsent(actionFromParent.getId(), actionFromParent);
                }
            } catch (RuntimeException e) {
                throw new RuntimeException("Can not parse action from parent", e);
            }
        }

        return new ArrayList<>(actionDtoMap.values());
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

        TypeMutRecord(TypeDto dto) {
            super(dto);
        }

        public String getModuleId() {
            return getId();
        }

        public void setModuleId(String value) {
            setId(value);
        }

        @JsonIgnore
        @MetaAtt(".disp")
        public String getDisplayName() {
            String result = getId();
            return result != null ? result : "Section";
        }

        @JsonProperty("_content")
        public void setContent(List<ObjectData> content) {

            String base64Content = content.get(0).get("url", "");
            base64Content = base64Content.replaceAll("^data:application/json;base64,", "");
            ObjectData data = Json.getMapper().read(Base64.getDecoder().decode(base64Content), ObjectData.class);

            Json.getMapper().applyData(this, data);
        }

        @JsonValue
        public TypeDto toJson() {
            return new TypeDto(this);
        }
    }

    @Data
    @AllArgsConstructor
    public static class RoleInfo {
        private String roleId;
        private MLText name;
    }
}
