package ru.citeck.ecos.model.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.dto.SectionDto;
import ru.citeck.ecos.model.record.SectionRecord;
import ru.citeck.ecos.model.service.SectionService;
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
public class SectionRecordsDao extends LocalRecordsDAO
                                   implements RecordsQueryWithMetaLocalDAO<SectionRecord>,
                                              RecordsMetaLocalDAO<SectionRecord> {

    private static final String ID = "section";
    private static final String LANGUAGE_EMPTY = "";

    private final SectionService sectionService;

    private final PredicateService predicateService;

    private static final SectionRecord EMPTY_RECORD = new SectionRecord(new SectionDto());

    @Autowired
    public SectionRecordsDao(SectionService sectionService,
                             PredicateService predicateService) {
        setId(ID);
        this.sectionService = sectionService;
        this.predicateService = predicateService;
    }

    @Override
    public List<SectionRecord> getMetaValues(List<RecordRef> list) {
        if (list.size() == 1 && list.get(0).getId().isEmpty()) {
            return Collections.singletonList(EMPTY_RECORD);
        }

        return list.stream()
            .map(ref -> new SectionRecord(sectionService.getByExtId(ref.toString())))
            .collect(Collectors.toList());
    }

    @Override
    public RecordsQueryResult<SectionRecord> getMetaValues(RecordsQuery query) {

        RecordsQueryResult<SectionRecord> result = new RecordsQueryResult<>();

        if (query.getLanguage().equals(PredicateService.LANGUAGE_PREDICATE)) {
            Predicate predicate = predicateService.readJson(query.getQuery());

            query.setSourceId(ID);
            query.setLanguage(LANGUAGE_EMPTY);

            Elements<RecordElement> elements = new RecordElements(recordsService, query);

            Set<String> filteredResultIds = predicateService.filter(elements, predicate).stream()
                .map(e -> e.getRecordRef().getId())
                .collect(Collectors.toSet());

            result.addRecords(sectionService.getAll(filteredResultIds).stream()
                .map(SectionRecord::new)
                .collect(Collectors.toList()));

        } else {
            result.setRecords(sectionService.getAll().stream()
                .map(SectionRecord::new)
                .collect(Collectors.toList()));
        }
        return result;
    }
}
