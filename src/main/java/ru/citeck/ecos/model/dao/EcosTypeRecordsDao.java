package ru.citeck.ecos.model.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.module.type.type.action.ActionDto;
import ru.citeck.ecos.model.dto.EcosTypeDto;
import ru.citeck.ecos.model.service.EcosTypeService;
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
import ru.citeck.ecos.records2.source.dao.local.RecordsMetaLocalDAO;
import ru.citeck.ecos.records2.source.dao.local.RecordsQueryWithMetaLocalDAO;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class EcosTypeRecordsDao extends LocalRecordsDAO
    implements RecordsQueryWithMetaLocalDAO<EcosTypeRecordsDao.EcosTypeRecord>,
    RecordsMetaLocalDAO<EcosTypeRecordsDao.EcosTypeRecord> {

    public static final String ID = "type";

    private static final String LANGUAGE_EMPTY = "";
    private final static ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final EcosTypeRecord EMPTY_RECORD = new EcosTypeRecord(new EcosTypeDto());

    private final EcosTypeService typeService;
    private final PredicateService predicateService;
    private RecordsService recordsService;

    @Autowired
    public EcosTypeRecordsDao(EcosTypeService typeService,
                              PredicateService predicateService,
                              @Lazy RecordsService recordsService) {
        setId(ID);
        this.typeService = typeService;
        this.predicateService = predicateService;
        this.recordsService = recordsService;
    }

    @Override
    public List<EcosTypeRecord> getMetaValues(List<RecordRef> list) {
        if (list.size() == 1 && list.get(0).getId().isEmpty()) {
            return Collections.singletonList(EMPTY_RECORD);
        }

        return list.stream()
            .map(ref -> new EcosTypeRecord(typeService.getByExtId(ref.toString())))
            .collect(Collectors.toList());
    }

    @Override
    public RecordsQueryResult<EcosTypeRecord> getMetaValues(RecordsQuery query) {

        RecordsQueryResult<EcosTypeRecord> result = new RecordsQueryResult<>();

        if (query.getLanguage().equals(PredicateService.LANGUAGE_PREDICATE)) {
            Predicate predicate = predicateService.readJson(query.getQuery());

            query.setSourceId(ID);
            query.setLanguage(LANGUAGE_EMPTY);

            Elements<RecordElement> elements = new RecordElements(recordsService, query);

            Set<String> filteredResultIds = predicateService.filter(elements, predicate).stream()
                .map(e -> e.getRecordRef().getId())
                .collect(Collectors.toSet());

            result.addRecords(typeService.getAll(filteredResultIds).stream()
                .map(EcosTypeRecord::new)
                .collect(Collectors.toList()));

        } else {
            result.setRecords(typeService.getAll().stream()
                .map(EcosTypeRecord::new)
                .collect(Collectors.toList()));
        }
        return result;
    }

    public class EcosTypeRecord implements MetaValue {

        private final EcosTypeDto dto;

        public EcosTypeRecord(EcosTypeDto dto) {
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

    //TODO: process override actions?
    private Set<ActionDto> getInheritTypeActions(EcosTypeDto dto) {
        if (!dto.isInheritActions() || dto.getParent() == null) {
            return dto.getActions();
        }

        Map<String, ActionDto> actionDtoMap = dto.getActions()
            .stream()
            .collect(Collectors.toMap(ActionDto::getId, Function.identity()));

        RecordRef parent = dto.getParent();
        JsonNode actionsNode = recordsService.getAttribute(parent, "_actions[]?json");

        if (actionsNode == null || actionsNode.isNull() || actionsNode.isMissingNode()) {
            return dto.getActions();
        }

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
