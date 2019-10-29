package ru.citeck.ecos.model.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.dto.EcosTypeDto;
import ru.citeck.ecos.model.record.EcosTypeRecord;
import ru.citeck.ecos.model.service.EcosTypeService;
import ru.citeck.ecos.predicate.Elements;
import ru.citeck.ecos.predicate.PredicateService;
import ru.citeck.ecos.predicate.model.Predicate;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.predicate.RecordElement;
import ru.citeck.ecos.records2.predicate.RecordElements;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDAO;
import ru.citeck.ecos.records2.source.dao.local.RecordsMetaLocalDAO;
import ru.citeck.ecos.records2.source.dao.local.RecordsQueryWithMetaLocalDAO;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class EcosTypeRecordsDao extends LocalRecordsDAO
    implements RecordsQueryWithMetaLocalDAO<EcosTypeRecord>,
    RecordsMetaLocalDAO<EcosTypeRecord> {

    private static final String ID = "type";
    private static final String LANGUAGE_EMPTY = "";

    private final EcosTypeService typeService;

    private final PredicateService predicateService;

    private static final EcosTypeRecord EMPTY_RECORD = new EcosTypeRecord(new EcosTypeDto());

    @Autowired
    public EcosTypeRecordsDao(EcosTypeService typeService,
                              PredicateService predicateService) {
        setId(ID);
        this.typeService = typeService;
        this.predicateService = predicateService;
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
}
