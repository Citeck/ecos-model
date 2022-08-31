package ru.citeck.ecos.model.section.records.dao;

import ecos.com.fasterxml.jackson210.annotation.JsonIgnore;
import ecos.com.fasterxml.jackson210.annotation.JsonProperty;
import ecos.com.fasterxml.jackson210.annotation.JsonValue;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.section.records.record.SectionRecord;
import ru.citeck.ecos.model.section.dto.SectionDto;
import ru.citeck.ecos.model.section.service.SectionService;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.RecordElement;
import ru.citeck.ecos.records2.predicate.RecordElements;
import ru.citeck.ecos.records2.predicate.element.Elements;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records2.request.delete.RecordsDelResult;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.MutableRecordsLocalDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDao;
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class SectionRecordsDao extends LocalRecordsDao
    implements LocalRecordsQueryWithMetaDao<SectionRecord>,
               LocalRecordsMetaDao<SectionRecord>,
               MutableRecordsLocalDao<SectionRecordsDao.SectionMutRecord> {

    private static final String ID = "section";
    private static final String LANGUAGE_EMPTY = "";

    private final SectionService sectionService;
    private final PredicateService predicateService;

    private final SectionRecord EMPTY_RECORD = new SectionRecord(new SectionDto());

    @Autowired
    public SectionRecordsDao(SectionService sectionService,
                             PredicateService predicateService) {
        setId(ID);
        this.sectionService = sectionService;
        this.predicateService = predicateService;
    }

    @Override
    public List<SectionRecord> getLocalRecordsMeta(List<RecordRef> list, MetaField metaField) {
        if (list.size() == 1 && list.get(0).getId().isEmpty()) {
            return Collections.singletonList(EMPTY_RECORD);
        }

        return list.stream()
            .map(ref -> new SectionRecord(sectionService.getByExtId(ref.getId())))
            .collect(Collectors.toList());
    }

    @Override
    public RecordsDelResult delete(RecordsDeletion recordsDeletion) {

        List<RecordMeta> result = new ArrayList<>();

        recordsDeletion.getRecords().forEach(r -> {
            sectionService.delete(r.getId());
            result.add(new RecordMeta(r));
        });

        RecordsDelResult delRes = new RecordsDelResult();
        delRes.setRecords(result);

        return delRes;
    }

    @Override
    public List<SectionMutRecord> getValuesToMutate(List<RecordRef> list) {
        return list.stream()
            .map(RecordRef::getId)
            .map(id -> {
                if (StringUtils.isBlank(id)) {
                    return new SectionMutRecord();
                } else {
                    return new SectionMutRecord(sectionService.getByExtId(id));
                }
            })
            .collect(Collectors.toList());
    }

    @Override
    public RecordsMutResult save(List<SectionMutRecord> list) {

        RecordsMutResult result = new RecordsMutResult();

        list.forEach(sec -> {
            SectionDto resDto = sectionService.save(sec);
            result.addRecord(new RecordMeta(RecordRef.valueOf(resDto.getId())));
        });

        return result;
    }

    @Override
    public RecordsQueryResult<SectionRecord> queryLocalRecords(RecordsQuery recordsQuery, MetaField metaField) {

        RecordsQueryResult<SectionRecord> result = new RecordsQueryResult<>();

        if (recordsQuery.getLanguage().equals(PredicateService.LANGUAGE_PREDICATE)) {
            Predicate predicate = recordsQuery.getQuery(Predicate.class);

            recordsQuery.setSourceId(ID);
            recordsQuery.setLanguage(LANGUAGE_EMPTY);

            Elements<RecordElement> elements = new RecordElements(recordsService, recordsQuery);

            Set<String> filteredResultIds = predicateService.filter(elements, predicate).stream()
                .map(e -> e.getRecordRef().getId())
                .collect(Collectors.toSet());

            result.addRecords(sectionService.getAll(filteredResultIds).stream()
                .map(SectionRecord::new)
                .collect(Collectors.toList()));

        } else {
            result.setRecords(sectionService.getAll(recordsQuery.getMaxItems(), recordsQuery.getSkipCount()).stream()
                .map(SectionRecord::new)
                .collect(Collectors.toList()));
            result.setTotalCount(sectionService.getCount());
        }
        return result;
    }


    public static class SectionMutRecord extends SectionDto {

        SectionMutRecord() {
        }

        SectionMutRecord(SectionDto dto) {
            super(dto);
        }

        @JsonIgnore
        public String getModuleId() {
            return getId();
        }

        public void setModuleId(String value) {
            setId(value);
        }

        @JsonIgnore
        @AttName("?disp")
        public String getDisplayName() {
            String result = getId();
            return result != null ? result : "Section";
        }

        @JsonProperty("_content")
        public void setContent(List<ObjectData> content) {

            String dataUriContent = content.get(0).get("url", "");
            ObjectData data = Json.getMapper().read(dataUriContent, ObjectData.class);

            Json.getMapper().applyData(this, data);
        }

        @JsonValue
        public SectionDto toJson() {
            return new SectionDto(this);
        }
    }
}
