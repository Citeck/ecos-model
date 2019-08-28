package ru.citeck.ecos.model.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.dto.EcosTypeDto;
import ru.citeck.ecos.model.record.EcosTypeRecord;
import ru.citeck.ecos.model.record.mutable.EcosTypeMutable;
import ru.citeck.ecos.model.service.EcosTypeService;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
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

    @Autowired
    public EcosTypeRecordsDao(EcosTypeService typeService) {
        setId(ID);
        this.typeService = typeService;
    }

    @Override
    public RecordsQueryResult<EcosTypeRecord> getMetaValues(RecordsQuery query) {
        RecordsQueryResult<EcosTypeRecord> result = new RecordsQueryResult<>();
        result.setRecords(typeService.getAll().stream()
            .map(EcosTypeRecord::new)
            .collect(Collectors.toList()));
        return null;
    }

    @Override
    public List<EcosTypeRecord> getMetaValues(List<RecordRef> records) {
        return records.stream().map(ref -> {
            if (ref.getId().isEmpty()) {
                throw new IllegalArgumentException("Empty ref is not supported!");
            }
            Long id = Long.valueOf(ref.toString());
            return new EcosTypeRecord(typeService.getById(id).orElseThrow(() ->
                new IllegalArgumentException("Record doesn't exists: " + ref)));
        }).collect(Collectors.toList());
    }

    @Override
    public List<EcosTypeMutable> getValuesToMutate(List<RecordRef> records) {

        Map<RecordRef, EcosTypeDto> instances = new HashMap<>();

        typeService.getAll(records.stream()
            .map(RecordRef::toString)
            .map(Long::new)
            .collect(Collectors.toList()))
            .forEach(dto -> {
                String idStr = String.valueOf(dto.getId());
                instances.put(RecordRef.valueOf(idStr), dto);
            });

        return records.stream().map(ref -> {
            if (instances.containsKey(ref)) {
                return new EcosTypeMutable(instances.get(ref));
            } else {
                EcosTypeMutable mutable = new EcosTypeMutable();
                Long id = Long.valueOf(ref.getId());
                mutable.setId(id);
                return mutable;
            }
        }).collect(Collectors.toList());
    }

    @Override
    public RecordsMutResult save(List<EcosTypeMutable> values) {

        List<RecordMeta> resultMeta = new ArrayList<>();
        values.stream()
            .filter(e -> e.getId() != null)
            .forEach(e -> {
                typeService.update(e);
                RecordRef ref = RecordRef.valueOf(String.valueOf(e.getId()));
                resultMeta.add(new RecordMeta(ref));
            });

        RecordsMutResult result = new RecordsMutResult();
        result.setRecords(resultMeta);
        return result;
    }

    @Override
    public RecordsDelResult delete(RecordsDeletion deletion) {
        RecordsDelResult result = new RecordsDelResult();

        deletion.getRecords().forEach(ref -> {
            Long id = Long.valueOf(ref.getId());
            typeService.delete(id);
            result.addRecord(new RecordMeta(ref));
        });

        return result;
    }
}
