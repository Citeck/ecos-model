package ru.citeck.ecos.model.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.dto.EcosSectionDto;
import ru.citeck.ecos.model.record.EcosSectionRecord;
import ru.citeck.ecos.model.record.mutable.EcosSectionMutable;
import ru.citeck.ecos.model.service.EcosSectionService;
import ru.citeck.ecos.predicate.Elements;
import ru.citeck.ecos.predicate.PredicateService;
import ru.citeck.ecos.predicate.model.Predicate;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.predicate.RecordElement;
import ru.citeck.ecos.records2.predicate.RecordElements;
import ru.citeck.ecos.records2.request.delete.RecordsDelResult;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDAO;
import ru.citeck.ecos.records2.source.dao.local.MutableRecordsLocalDAO;
import ru.citeck.ecos.records2.source.dao.local.RecordsMetaLocalDAO;
import ru.citeck.ecos.records2.source.dao.local.RecordsQueryWithMetaLocalDAO;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class EcosSectionRecordsDao extends LocalRecordsDAO
                                   implements RecordsQueryWithMetaLocalDAO<EcosSectionRecord>,
                                              RecordsMetaLocalDAO<EcosSectionRecord>,
                                              MutableRecordsLocalDAO<EcosSectionMutable> {

    private static final String ID = "section";
    private static final String LANGUAGE_EMPTY = "";

    private final EcosSectionService sectionService;

    private final PredicateService predicateService;

    private static final EcosSectionRecord EMPTY_RECORD = new EcosSectionRecord(new EcosSectionDto());

    @Autowired
    public EcosSectionRecordsDao(EcosSectionService sectionService,
                                     PredicateService predicateService) {
        setId(ID);
        this.sectionService = sectionService;
        this.predicateService = predicateService;
    }

    @Override
    public List<EcosSectionMutable> getValuesToMutate(List<RecordRef> records) {

        Map<String, EcosSectionDto> stored =
            sectionService.getAll(
                records.stream()
                    .map(RecordRef::getId)
                    .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(EcosSectionDto::getId, dto -> dto));

        return records.stream()
            .map(RecordRef::getId)
            .map(id -> {
                if (stored.containsKey(id)) {
                    return new EcosSectionMutable(stored.get(id));
                } else {
                    return new EcosSectionMutable(id);
                }
            }).collect(Collectors.toList());
    }

    @Override
    public RecordsMutResult save(List<EcosSectionMutable> values) {

        RecordsMutResult result = new RecordsMutResult();

        result.setRecords(values.stream()
            .filter(e -> e.getId() != null)
            .map(e -> {
                EcosSectionDto storedDto = sectionService.update(e);
                RecordRef ref = RecordRef.valueOf(storedDto.getId());
                RecordMeta meta = new RecordMeta(ref);
                meta.setAttribute("id", storedDto.getId());
                meta.setAttribute("name", storedDto.getName());
                meta.setAttribute("description", storedDto.getDescription());
                meta.setAttribute("tenant", storedDto.getTenant());
                return meta;
            })
            .collect(Collectors.toList()));

        return result;
    }

    @Override
    public RecordsDelResult delete(RecordsDeletion recordsDeletion) {

        RecordsDelResult result = new RecordsDelResult();

        result.addRecords(
            recordsDeletion.getRecords().stream()
                .filter(ref -> ref.getId() != null)
                .map(ref -> {
                    sectionService.delete(ref.getId());
                    return new RecordMeta(ref);
                })
                .collect(Collectors.toList()));

        return result;
    }

    @Override
    public List<EcosSectionRecord> getMetaValues(List<RecordRef> list) {
        if (list.size() == 1 && list.get(0).getId().isEmpty()) {
            return Collections.singletonList(EMPTY_RECORD);
        }

        return list.stream()
            .map(ref -> new EcosSectionRecord(sectionService.getByExtId(ref.toString())))
            .collect(Collectors.toList());
    }

    @Override
    public RecordsQueryResult<EcosSectionRecord> getMetaValues(RecordsQuery query) {

        RecordsQueryResult<EcosSectionRecord> result = new RecordsQueryResult<>();

        if (query.getLanguage().equals(PredicateService.LANGUAGE_PREDICATE)) {
            Predicate predicate = predicateService.readJson(query.getQuery());

            query.setSourceId(ID);
            query.setLanguage(LANGUAGE_EMPTY);

            Elements<RecordElement> elements = new RecordElements(recordsService, query);

            Set<String> filteredResultIds = predicateService.filter(elements, predicate).stream()
                .map(e -> e.getRecordRef().getId())
                .collect(Collectors.toSet());

            result.addRecords(sectionService.getAll(filteredResultIds).stream()
                .map(EcosSectionRecord::new)
                .collect(Collectors.toList()));

        } else {
            result.setRecords(sectionService.getAll().stream()
                .map(EcosSectionRecord::new)
                .collect(Collectors.toList()));
        }
        return result;
    }
}
