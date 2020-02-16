package ru.citeck.ecos.model.dao;

import ecos.com.fasterxml.jackson210.core.JsonProcessingException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records2.objdata.DataValue;
import ru.citeck.ecos.records2.predicate.Elements;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records2.scalar.MLText;
import ru.citeck.ecos.apps.app.module.EappsModuleService;
import ru.citeck.ecos.apps.app.module.ModuleRef;
import ru.citeck.ecos.apps.app.module.type.form.FormModule;
import ru.citeck.ecos.model.dto.TypeAssociationDto;
import ru.citeck.ecos.model.dto.TypeDto;
import ru.citeck.ecos.model.service.TypeService;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.records2.predicate.RecordElement;
import ru.citeck.ecos.records2.predicate.RecordElements;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDAO;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDAO;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDAO;
import ru.citeck.ecos.records2.utils.json.JsonUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class TypeRecordsDao extends LocalRecordsDAO
    implements LocalRecordsQueryWithMetaDAO<TypeRecordsDao.TypeRecord>, LocalRecordsMetaDAO<TypeRecordsDao.TypeRecord> {

    public static final String ID = "type";

    private static final String LANGUAGE_EMPTY = "";
    private static final String TYPE_ACTIONS_WITH_INHERIT_ATT_JSON = "_actions[]?json";
    private static final String UISERV_EFORM_PREFIX = "uiserv/eform@";

    private final TypeRecord EMPTY_RECORD = new TypeRecord(new TypeDto());

    private final TypeService typeService;
    private final PredicateService predicateService;
    private RecordsService recordsService;

    private final String formTypeIdPrefix;

    @Autowired
    public TypeRecordsDao(TypeService typeService,
                          PredicateService predicateService,
                          @Lazy RecordsService recordsService,
                          EappsModuleService moduleService) {
        setId(ID);
        this.typeService = typeService;
        this.predicateService = predicateService;
        this.recordsService = recordsService;

        formTypeIdPrefix = moduleService.getTypeId(FormModule.class) + "$";
    }

    @Override
    public List<TypeRecord> getLocalRecordsMeta(List<RecordRef> list, MetaField metaField) {
        if (list.size() == 1 && list.get(0).getId().isEmpty()) {
            return Collections.singletonList(EMPTY_RECORD);
        }

        return list.stream()
            .map(ref -> new TypeRecord(typeService.getByExtId(ref.getId())))
            .collect(Collectors.toList());
    }

    @Override
    public RecordsQueryResult<TypeRecord> queryLocalRecords(RecordsQuery recordsQuery, MetaField metaField) {

        RecordsQueryResult<TypeRecord> result = new RecordsQueryResult<>();

        if (recordsQuery.getLanguage().equals(PredicateService.LANGUAGE_PREDICATE)) {

            Predicate predicate = recordsQuery.getQuery(Predicate.class);

            recordsQuery.setSourceId(ID);
            recordsQuery.setLanguage(LANGUAGE_EMPTY);

            Elements<RecordElement> elements = new RecordElements(recordsService, recordsQuery);

            Set<String> filteredResultIds = predicateService.filter(elements, predicate).stream()
                .map(e -> e.getRecordRef().getId())
                .collect(Collectors.toSet());

            result.addRecords(typeService.getAll(filteredResultIds).stream()
                .map(TypeRecord::new)
                .collect(Collectors.toList()));

        } else {
            result.setRecords(typeService.getAll().stream()
                .map(TypeRecord::new)
                .collect(Collectors.toList()));
        }
        return result;
    }

    /**
     * This class must be like {@link ru.citeck.ecos.model.dao.record.SectionRecord} in dedicated '.class' file,
     * but we don't need to set inherited type actions in dto or record.
     */
    public class TypeRecord implements MetaValue {

        private final TypeDto dto;

        public TypeRecord(TypeDto dto) {
            this.dto = dto;
        }

        @Override
        public String getId() {
            return dto.getId();
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
                    return dto.getId();
                case "description":
                    return dto.getDescription();
                case "tenant":
                    return dto.getTenant();
                case "inheritActions":
                    return dto.isInheritActions();
                case "parent":
                case RecordConstants.ATT_PARENT:
                    return dto.getParent();
                case "actions":
                    return dto.getActions();
                case RecordConstants.ATT_ACTIONS:
                    return getInheritTypeActions(dto);
                case "associations":
                    return dto.getAssociations();
                case "assocsFull":
                    return getTypeAndParentsAssociations(dto);
                case "form":
                    String formId = dto.getForm();
                    if (StringUtils.isNotBlank(formId)) {
                        if (formId.startsWith(formTypeIdPrefix)) {
                            formId = formId.substring(formTypeIdPrefix.length());
                        }
                        return RecordRef.valueOf(UISERV_EFORM_PREFIX + formId);
                    }
                    return null;
                case "inheritedForm":
                    return findAndGetInheritedForm(dto);
                case "attributes":
                    return dto.getAttributes();
                case "createVariants":
                    return dto.getCreateVariants();
            }
            return null;
        }

        @Override
        public Object getJson() {
            return dto;
        }
    }

    private RecordRef findAndGetInheritedForm(TypeDto typeDto) {

        TypeDto currentType = typeDto;

        while (currentType != null) {

            String formId = currentType.getForm();

            if (formId != null) {
                return RecordRef.valueOf(UISERV_EFORM_PREFIX + formId);
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

    private Set<TypeAssociationDto> getTypeAndParentsAssociations(TypeDto typeDto) {

        Set<TypeAssociationDto> resultAssociations = new HashSet<>();

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

    private Set<ModuleRef> getInheritTypeActions(TypeDto dto) {

        if (!dto.isInheritActions() || dto.getParent() == null) {
            return dto.getActions();
        }

        RecordRef parent = dto.getParent();
        DataValue actionsNode = recordsService.getAttribute(parent, TYPE_ACTIONS_WITH_INHERIT_ATT_JSON);

        if (actionsNode == null || actionsNode.isNull()) {
            return dto.getActions();
        }

        Map<String, ModuleRef> actionDtoMap = dto.getActions()
            .stream()
            .collect(Collectors.toMap(ModuleRef::getId, Function.identity()));

        if (actionsNode.isArray()) {
            ModuleRef[] actionsFromParent;
            try {
                actionsFromParent = JsonUtils.convert(actionsNode, ModuleRef[].class);
                for (ModuleRef actionDto : actionsFromParent) {
                    actionDtoMap.putIfAbsent(actionDto.getId(), actionDto);
                }
            } catch (RuntimeException e) {
                throw new RuntimeException("Can not parse actions from parent", e);
            }
        } else {
            try {
                ModuleRef actionFromParent = JsonUtils.convert(actionsNode, ModuleRef.class);
                actionDtoMap.putIfAbsent(actionFromParent.getId(), actionFromParent);
            } catch (RuntimeException e) {
                throw new RuntimeException("Can not parse action from parent", e);
            }
        }

        return new HashSet<>(actionDtoMap.values());
    }

}
