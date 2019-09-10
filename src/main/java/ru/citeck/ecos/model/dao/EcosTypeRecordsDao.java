package ru.citeck.ecos.model.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.dto.EcosTypeDto;
import ru.citeck.ecos.model.record.EcosTypeRecord;
import ru.citeck.ecos.model.record.mutable.EcosTypeMutable;
import ru.citeck.ecos.model.service.EcosTypeService;
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
public class EcosTypeRecordsDao extends LocalRecordsDAO
                                implements RecordsQueryWithMetaLocalDAO<EcosTypeRecord>,
                                           RecordsMetaLocalDAO<EcosTypeRecord>,
                                           MutableRecordsLocalDAO<EcosTypeMutable> {

    private static final String ID = "type";

    private final EcosTypeService typeService;

    private final PredicateService predicateService;

    @Autowired
    public EcosTypeRecordsDao(EcosTypeService typeService,
                              PredicateService predicateService) {
        setId(ID);
        this.typeService = typeService;
        this.predicateService = predicateService;
    }

    @Override
    public RecordsQueryResult<EcosTypeRecord> getMetaValues(RecordsQuery query) {

        RecordsQueryResult<EcosTypeRecord> result = new RecordsQueryResult<>();

        if (query.getLanguage().equals(PredicateService.LANGUAGE_PREDICATE)) {
            Predicate predicate = predicateService.readJson(query.getQuery());

            query.setSourceId("type");
            query.setLanguage("");
            Elements<RecordElement> elements = new RecordElements(recordsService, query);

            List<RecordElement> result2 = predicateService.filter(elements, predicate);

            RecordsQueryResult<EcosTypeRecord> resultPredicate = new RecordsQueryResult<>();
            result2.forEach(e -> {
                resultPredicate.addRecord(
                    typeService.getByUuid(e.getRecordRef().getId())
                        .map(EcosTypeRecord::new).orElse(null));
            });

            return resultPredicate;
        }

        result.setRecords(typeService.getAll().stream()
            .map(EcosTypeRecord::new)
            .collect(Collectors.toList()));
        return result;
    }

    @Override
    public List<EcosTypeRecord> getMetaValues(List<RecordRef> records) {
        return records.stream().map(ref -> {
            if (ref.getId().isEmpty()) {
                return new EcosTypeRecord(new EcosTypeDto());
            }
            return new EcosTypeRecord(typeService.getByUuid(ref.toString()).orElseThrow(() ->
                new IllegalArgumentException("Record doesn't exists: " + ref)));
        }).collect(Collectors.toList());
    }

    @Override
    public List<EcosTypeMutable> getValuesToMutate(List<RecordRef> records) {

        Map<RecordRef, EcosTypeDto> instances = new HashMap<>();

        typeService.getAll(records.stream()
            .map(RecordRef::toString)
            .collect(Collectors.toList()))
            .forEach(dto -> {
                instances.put(RecordRef.valueOf(dto.getExtId()), dto);
            });

        return records.stream().map(ref -> {
            if (instances.containsKey(ref)) {
                return new EcosTypeMutable(instances.get(ref));
            } else {
                EcosTypeMutable mutable = new EcosTypeMutable();
                mutable.setExtId(ref.getId());
                return mutable;
            }
        }).collect(Collectors.toList());
    }

    @Override
    public RecordsMutResult save(List<EcosTypeMutable> values) {

        List<RecordMeta> resultMeta = new ArrayList<>();
        values.stream()
            .filter(e -> e.getExtId() != null)
            .forEach(e -> {
                EcosTypeDto storedDto = typeService.update(e);
                RecordRef ref = RecordRef.valueOf(storedDto.getExtId());
                RecordMeta meta = new RecordMeta(ref);
                meta.setAttribute("extId", storedDto.getExtId());
                meta.setAttribute("name", storedDto.getName());
                meta.setAttribute("description", storedDto.getDescription());
                meta.setAttribute("tenant", storedDto.getTenant());
                RecordRef parent = storedDto.getParent();
                if (parent != null) {
                    meta.setAttribute("parent", parent.toString());
                }
                resultMeta.add(meta);
            });

        RecordsMutResult result = new RecordsMutResult();
        result.setRecords(resultMeta);
        return result;
    }

    @Override
    public RecordsDelResult delete(RecordsDeletion deletion) {
        RecordsDelResult result = new RecordsDelResult();

        deletion.getRecords().forEach(ref -> {
            typeService.delete(ref.getId());
            result.addRecord(new RecordMeta(ref));
        });

        return result;
    }
}
