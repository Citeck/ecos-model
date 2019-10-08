package ru.citeck.ecos.model.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.dto.EcosAssociationDto;
import ru.citeck.ecos.model.record.EcosAssociationRecord;
import ru.citeck.ecos.model.record.mutable.EcosAssociationMutable;
import ru.citeck.ecos.model.service.EcosAssociationService;
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
public class EcosAssociationRecordsDao extends LocalRecordsDAO
                                       implements RecordsQueryWithMetaLocalDAO<EcosAssociationRecord>,
                                                  RecordsMetaLocalDAO<EcosAssociationRecord>,
                                                  MutableRecordsLocalDAO<EcosAssociationMutable> {

    private static final String ID = "association";
    private static final String LANGUAGE_EMPTY = "";

    private final EcosAssociationService associationService;

    private final PredicateService predicateService;

    private static final EcosAssociationRecord EMPTY_RECORD = new EcosAssociationRecord(new EcosAssociationDto());

    @Autowired
    public EcosAssociationRecordsDao(EcosAssociationService associationService,
                                     PredicateService predicateService) {
        setId(ID);
        this.associationService = associationService;
        this.predicateService = predicateService;
    }

    @Override
    public List<EcosAssociationMutable> getValuesToMutate(List<RecordRef> records) {

        Map<String, EcosAssociationDto> stored =
            associationService.getAll(
                records.stream()
                    .map(RecordRef::getId)
                    .collect(Collectors.toSet()))
                .stream()
                    .collect(Collectors.toMap(EcosAssociationDto::getId, dto -> dto));

        return records.stream()
            .map(RecordRef::getId)
            .map(id -> {
                    if (stored.containsKey(id)) {
                        return new EcosAssociationMutable(stored.get(id));
                    } else {
                        return new EcosAssociationMutable(id);
                    }
                }).collect(Collectors.toList());
    }

    @Override
    public RecordsMutResult save(List<EcosAssociationMutable> values) {

        RecordsMutResult result = new RecordsMutResult();

        result.setRecords(values.stream()
            .filter(e -> e.getId() != null)
            .map(e -> {
                EcosAssociationDto storedDto = associationService.update(e);
                RecordRef ref = RecordRef.valueOf(storedDto.getId());
                RecordMeta meta = new RecordMeta(ref);
                meta.setAttribute("id", storedDto.getId());
                meta.setAttribute("name", storedDto.getName());
                meta.setAttribute("title", storedDto.getTitle());
                RecordRef source = storedDto.getSourceType();
                meta.setAttribute("source", source.toString());
                RecordRef target = storedDto.getTargetType();
                meta.setAttribute("target", target.toString());
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
                    associationService.delete(ref.getId());
                    return new RecordMeta(ref);
                })
                .collect(Collectors.toList()));

        return result;
    }

    @Override
    public List<EcosAssociationRecord> getMetaValues(List<RecordRef> list) {
        if (list.size() == 1 && list.get(0).getId().isEmpty()) {
            return Collections.singletonList(EMPTY_RECORD);
        }

        return list.stream()
            .map(ref -> new EcosAssociationRecord(associationService.getByExtId(ref.toString())))
            .collect(Collectors.toList());
    }

    @Override
    public RecordsQueryResult<EcosAssociationRecord> getMetaValues(RecordsQuery query) {

        RecordsQueryResult<EcosAssociationRecord> result = new RecordsQueryResult<>();

        if (query.getLanguage().equals(PredicateService.LANGUAGE_PREDICATE)) {
            Predicate predicate = predicateService.readJson(query.getQuery());

            query.setSourceId(ID);
            query.setLanguage(LANGUAGE_EMPTY);

            Elements<RecordElement> elements = new RecordElements(recordsService, query);

            Set<String> filteredResultIds = predicateService.filter(elements, predicate).stream()
                .map(e -> e.getRecordRef().getId())
                .collect(Collectors.toSet());

            result.addRecords(associationService.getAll(filteredResultIds).stream()
                    .map(EcosAssociationRecord::new)
                    .collect(Collectors.toList()));

        } else {
            result.setRecords(associationService.getAll().stream()
                .map(EcosAssociationRecord::new)
                .collect(Collectors.toList()));
        }
        return result;
    }
}
