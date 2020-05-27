package ru.citeck.ecos.model.type.records.dao;

import ecos.com.fasterxml.jackson210.annotation.JsonIgnore;
import ecos.com.fasterxml.jackson210.annotation.JsonProperty;
import ecos.com.fasterxml.jackson210.annotation.JsonValue;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.association.dto.AssociationDto;
import ru.citeck.ecos.model.section.records.record.SectionRecord;
import ru.citeck.ecos.model.type.dto.TypeDto;
import ru.citeck.ecos.model.type.dto.TypeWithMetaDto;
import ru.citeck.ecos.model.type.service.TypeService;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records2.request.delete.RecordsDelResult;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDAO;
import ru.citeck.ecos.records2.source.dao.local.MutableRecordsLocalDAO;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDAO;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDAO;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class TypeRecordsDao extends LocalRecordsDAO
    implements LocalRecordsQueryWithMetaDAO<TypeRecordsDao.TypeRecord>,
            LocalRecordsMetaDAO<TypeRecordsDao.TypeRecord>,
            MutableRecordsLocalDAO<TypeRecordsDao.TypeMutRecord> {

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
    public List<TypeRecord> getLocalRecordsMeta(List<RecordRef> list, MetaField metaField) {

        if (list.size() == 1 && list.get(0).getId().isEmpty()) {
            return Collections.singletonList(EMPTY_RECORD);
        }

        return list.stream()
            .map(ref -> new TypeRecord(typeService.getOrCreateByExtId(ref.getId())))
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
                    }
                    return s.isAscending() ? Sort.Order.asc(attribute) : Sort.Order.desc(attribute);
                })
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
                case RecordConstants.ATT_PARENT:
                    return dto.getParent();
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
                    return getTypeAndParentsAssociations(dto);
                case "form":
                    return dto.getForm();
                case "journal":
                    return dto.getJournal();
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
                case "configForm":
                    return dto.getConfigForm();
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
                case RecordConstants.ATT_ECOS_TYPE:
                    return RecordRef.create("emodel", "type", "type");
                case "numTemplateRef":
                    return dto.getNumTemplateRef();
            }
            return null;
        }

        @Override
        public Object getJson() {
            return dto;
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

            RecordRef formId = currentType.getForm();

            if (formId != null) {
                return formId;
            } else {
                if (currentType.getParent() != null) {
                    currentType = typeService.getByExtId(currentType.getParent().getId());
                } else {
                    return null;
                }
            }
        }

        return null;
    }

    private Set<AssociationDto> getTypeAndParentsAssociations(TypeDto typeDto) {

        Set<AssociationDto> resultAssociations = new HashSet<>();

        TypeDto currentType = typeDto;
        while (currentType != null) {

            resultAssociations.addAll(currentType.getAssociations());

            RecordRef parentRecordRef = currentType.getParent();
            if (parentRecordRef != null) {
                currentType = typeService.getByExtId(currentType.getParent().getId());
            } else {
                currentType = null;
            }
        }

        return resultAssociations;
    }

    private List<RecordRef> getInheritTypeActions(TypeDto dto) {

        if (!dto.isInheritActions() || dto.getParent() == null) {
            return dto.getActions();
        }

        RecordRef parent = dto.getParent();
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
}
