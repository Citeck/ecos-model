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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class EcosAssociationRecordsDao extends LocalRecordsDAO
                                       implements RecordsQueryWithMetaLocalDAO<EcosAssociationRecord>,
                                                  RecordsMetaLocalDAO<EcosAssociationRecord>,
                                                  MutableRecordsLocalDAO<EcosAssociationMutable> {

    private static final String ID = "association";

    private final EcosAssociationService associationService;

    private final PredicateService predicateService;

    @Autowired
    public EcosAssociationRecordsDao(EcosAssociationService associationService,
                                     PredicateService predicateService) {
        setId(ID);
        this.associationService = associationService;
        this.predicateService = predicateService;
    }

    @Override
    public List<EcosAssociationMutable> getValuesToMutate(List<RecordRef> records) {
        Map<RecordRef, EcosAssociationDto> instances = new HashMap<>();

        associationService.getAll(records.stream()
            .map(RecordRef::toString)
            .collect(Collectors.toSet()))
            .forEach(dto -> {
                instances.put(RecordRef.valueOf(dto.getExtId()), dto);
            });

        return records.stream().map(ref -> {
            if (instances.containsKey(ref)) {
                return new EcosAssociationMutable(instances.get(ref));
            } else {
                EcosAssociationMutable mutable = new EcosAssociationMutable();
                mutable.setExtId(ref.getId());
                return mutable;
            }
        }).collect(Collectors.toList());
    }

    @Override
    public RecordsMutResult save(List<EcosAssociationMutable> values) {
        List<RecordMeta> resultMeta = new ArrayList<>();
        values.stream()
            .filter(e -> e.getExtId() != null)
            .forEach(e -> {
                EcosAssociationDto storedDto = associationService.update(e);
                RecordRef ref = RecordRef.valueOf(storedDto.getExtId());
                RecordMeta meta = new RecordMeta(ref);
                meta.setAttribute("extId", storedDto.getExtId());
                meta.setAttribute("name", storedDto.getName());
                meta.setAttribute("title", storedDto.getTitle());
                RecordRef type = storedDto.getType();
                if (type != null) {
                    meta.setAttribute("type", type.toString());
                }
                resultMeta.add(meta);
            });

        RecordsMutResult result = new RecordsMutResult();
        result.setRecords(resultMeta);
        return result;
    }

    @Override
    public RecordsDelResult delete(RecordsDeletion recordsDeletion) {
        RecordsDelResult result = new RecordsDelResult();

        recordsDeletion.getRecords().forEach(ref -> {
            associationService.delete(ref.getId());
            result.addRecord(new RecordMeta(ref));
        });

        return result;
    }

    @Override
    public List<EcosAssociationRecord> getMetaValues(List<RecordRef> list) {
        return list.stream().map(ref -> {
            if (ref.getId().isEmpty()) {
                return new EcosAssociationRecord(new EcosAssociationDto());
            }
            return new EcosAssociationRecord(associationService.getByExtId(ref.toString()).orElseThrow(() ->
                new IllegalArgumentException("Record doesn't exists: " + ref)));
        }).collect(Collectors.toList());
    }

    @Override
    public RecordsQueryResult<EcosAssociationRecord> getMetaValues(RecordsQuery query) {
        RecordsQueryResult<EcosAssociationRecord> result = new RecordsQueryResult<>();

        if (query.getLanguage().equals(PredicateService.LANGUAGE_PREDICATE)) {
            Predicate predicate = predicateService.readJson(query.getQuery());

            query.setSourceId("association");
            query.setLanguage("");
            Elements<RecordElement> elements = new RecordElements(recordsService, query);

            List<RecordElement> filtredResult = predicateService.filter(elements, predicate);

            RecordsQueryResult<EcosAssociationRecord> resultPredicate = new RecordsQueryResult<>();
            filtredResult.forEach(e -> {
                resultPredicate.addRecord(
                    associationService.getByExtId(e.getRecordRef().getId())
                        .map(EcosAssociationRecord::new).orElse(null));
            });

            return resultPredicate;
        }

        result.setRecords(associationService.getAll().stream()
            .map(EcosAssociationRecord::new)
            .collect(Collectors.toList()));
        return result;
    }
}
