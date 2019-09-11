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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class EcosSectionRecordsDao extends LocalRecordsDAO
    implements RecordsQueryWithMetaLocalDAO<EcosSectionRecord>,
    RecordsMetaLocalDAO<EcosSectionRecord>,
    MutableRecordsLocalDAO<EcosSectionMutable> {

    private static final String ID = "section";

    private final EcosSectionService sectionService;

    private final PredicateService predicateService;

    @Autowired
    public EcosSectionRecordsDao(EcosSectionService sectionService,
                              PredicateService predicateService) {
        setId(ID);
        this.sectionService = sectionService;
        this.predicateService = predicateService;
    }

    @Override
    public RecordsQueryResult<EcosSectionRecord> getMetaValues(RecordsQuery query) {

        RecordsQueryResult<EcosSectionRecord> result = new RecordsQueryResult<>();

        if (query.getLanguage().equals(PredicateService.LANGUAGE_PREDICATE)) {
            Predicate predicate = predicateService.readJson(query.getQuery());

            query.setSourceId("section");
            query.setLanguage("");
            Elements<RecordElement> elements = new RecordElements(recordsService, query);

            List<RecordElement> result2 = predicateService.filter(elements, predicate);

            RecordsQueryResult<EcosSectionRecord> resultPredicate = new RecordsQueryResult<>();
            result2.forEach(e -> {
                resultPredicate.addRecord(
                    sectionService.getByUuid(e.getRecordRef().getId())
                        .map(EcosSectionRecord::new).orElse(null));
            });

            return resultPredicate;
        }

        result.setRecords(sectionService.getAll().stream()
            .map(EcosSectionRecord::new)
            .collect(Collectors.toList()));
        return result;
    }

    @Override
    public List<EcosSectionRecord> getMetaValues(List<RecordRef> records) {
        return records.stream().map(ref -> {
            if (ref.getId().isEmpty()) {
                return new EcosSectionRecord(new EcosSectionDto());
            }
            return new EcosSectionRecord(sectionService.getByUuid(ref.toString()).orElseThrow(() ->
                new IllegalArgumentException("Record doesn't exists: " + ref)));
        }).collect(Collectors.toList());
    }

    @Override
    public List<EcosSectionMutable> getValuesToMutate(List<RecordRef> records) {

        Map<RecordRef, EcosSectionDto> instances = new HashMap<>();

        sectionService.getAll(records.stream()
            .map(RecordRef::toString)
            .collect(Collectors.toSet()))
            .forEach(dto -> {
                instances.put(RecordRef.valueOf(dto.getExtId()), dto);
            });

        return records.stream().map(ref -> {
            if (instances.containsKey(ref)) {
                return new EcosSectionMutable(instances.get(ref));
            } else {
                EcosSectionMutable mutable = new EcosSectionMutable();
                mutable.setExtId(ref.getId());
                return mutable;
            }
        }).collect(Collectors.toList());
    }

    @Override
    public RecordsMutResult save(List<EcosSectionMutable> values) {

        List<RecordMeta> resultMeta = new ArrayList<>();
        values.stream()
            .filter(e -> e.getExtId() != null)
            .forEach(e -> {
                EcosSectionDto storedDto = sectionService.update(e);
                RecordRef ref = RecordRef.valueOf(storedDto.getExtId());
                RecordMeta meta = new RecordMeta(ref);
                meta.setAttribute("extId", storedDto.getExtId());
                meta.setAttribute("name", storedDto.getName());
                meta.setAttribute("description", storedDto.getDescription());
                meta.setAttribute("tenant", storedDto.getTenant());
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
            sectionService.delete(ref.getId());
            result.addRecord(new RecordMeta(ref));
        });

        return result;
    }
}
