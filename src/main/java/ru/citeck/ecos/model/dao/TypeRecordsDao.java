package ru.citeck.ecos.model.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.module.type.type.action.ActionDto;
import ru.citeck.ecos.model.dto.TypeDto;
import ru.citeck.ecos.model.service.TypeService;
import ru.citeck.ecos.predicate.Elements;
import ru.citeck.ecos.predicate.PredicateService;
import ru.citeck.ecos.predicate.model.Predicate;
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

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class TypeRecordsDao extends LocalRecordsDAO
    implements LocalRecordsQueryWithMetaDAO<TypeRecordsDao.TypeRecord>, LocalRecordsMetaDAO<TypeRecordsDao.TypeRecord> {

    public static final String ID = "type";

    private static final String LANGUAGE_EMPTY = "";
    private static final String TYPE_ACTIONS_WITH_INHERIT_ATT_JSON = "_actions[]?json";
    private final static ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TypeRecord EMPTY_RECORD = new TypeRecord(new TypeDto());

    private final TypeService typeService;
    private final PredicateService predicateService;
    private RecordsService recordsService;

    @Autowired
    public TypeRecordsDao(TypeService typeService,
                          PredicateService predicateService,
                          @Lazy RecordsService recordsService) {
        setId(ID);
        this.typeService = typeService;
        this.predicateService = predicateService;
        this.recordsService = recordsService;
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
            Predicate predicate = predicateService.readJson(recordsQuery.getQuery());

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
            String dispName = dto.getName();
            if (dispName == null) {
                dispName = dto.getId();
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
            }
            return null;
        }

        @Override
        public Object getJson() {
            return dto;
        }

    }

    private Set<ActionDto> getInheritTypeActions(TypeDto dto) {
        if (!dto.isInheritActions() || dto.getParent() == null) {
            return dto.getActions();
        }

        RecordRef parent = dto.getParent();
        JsonNode actionsNode = recordsService.getAttribute(parent, TYPE_ACTIONS_WITH_INHERIT_ATT_JSON);

        if (actionsNode == null || actionsNode.isNull() || actionsNode.isMissingNode()) {
            return dto.getActions();
        }

        Map<String, ActionDto> actionDtoMap = dto.getActions()
            .stream()
            .collect(Collectors.toMap(ActionDto::getId, Function.identity()));

        if (actionsNode.isArray()) {
            ActionDto[] actionsFromParent;
            try {
                actionsFromParent = OBJECT_MAPPER.treeToValue(actionsNode, ActionDto[].class);
                for (ActionDto actionDto : actionsFromParent) {
                    actionDtoMap.putIfAbsent(actionDto.getId(), actionDto);
                }
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Can not parse actions from parent", e);
            }
        } else {
            try {
                ActionDto actionFromParent = OBJECT_MAPPER.treeToValue(actionsNode, ActionDto.class);
                actionDtoMap.putIfAbsent(actionFromParent.getId(), actionFromParent);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Can not parse action from parent", e);
            }
        }

        return new HashSet<>(actionDtoMap.values());
    }

}
