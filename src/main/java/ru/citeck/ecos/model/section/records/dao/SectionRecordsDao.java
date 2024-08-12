package ru.citeck.ecos.model.section.records.dao;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.section.records.record.SectionRecord;
import ru.citeck.ecos.model.section.dto.SectionDto;
import ru.citeck.ecos.model.section.service.SectionService;
import ru.citeck.ecos.records2.predicate.element.raw.RawElements;
import ru.citeck.ecos.records3.iter.IterableRecordRefs;
import ru.citeck.ecos.records3.iter.IterableRecordsConfig;
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao;
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao;
import ru.citeck.ecos.records3.record.dao.delete.DelStatus;
import ru.citeck.ecos.records3.record.dao.delete.RecordsDeleteDao;
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDtoDao;
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao;
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery;
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes;
import ru.citeck.ecos.webapp.api.entity.EntityRef;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class SectionRecordsDao extends AbstractRecordsDao
    implements RecordsQueryDao,
    RecordAttsDao,
    RecordsDeleteDao,
    RecordMutateDtoDao<SectionRecordsDao.SectionMutRecord> {

    private static final String ID = "section";
    private static final String LANGUAGE_EMPTY = "";

    private final SectionService sectionService;
    private final PredicateService predicateService;

    private final SectionRecord EMPTY_RECORD = new SectionRecord(new SectionDto());

    @Autowired
    public SectionRecordsDao(SectionService sectionService,
                             PredicateService predicateService) {

        this.sectionService = sectionService;
        this.predicateService = predicateService;
    }

    @Nullable
    @Override
    public Object getRecordAtts(@NotNull String recordId) {
        if (recordId.isEmpty()) {
            return Collections.singletonList(EMPTY_RECORD);
        }
        return new SectionRecord(sectionService.getByExtId(recordId));
    }

    @NotNull
    @Override
    public List<DelStatus> delete(@NotNull List<String> recordIds) throws Exception {

        List<DelStatus> statuses = new ArrayList<>();

        for (String recordId : recordIds) {
            sectionService.delete(recordId);
            statuses.add(DelStatus.OK);
        }

        return statuses;
    }

    @Override
    public SectionMutRecord getRecToMutate(@NotNull String recordId) throws Exception {
        if (StringUtils.isBlank(recordId)) {
            return new SectionMutRecord();
        } else {
            return new SectionMutRecord(sectionService.getByExtId(recordId));
        }
    }

    @NotNull
    @Override
    public String saveMutatedRec(SectionMutRecord sectionMutRecord) throws Exception {
        return sectionService.save(sectionMutRecord).getId();
    }

    @Nullable
    @Override
    public Object queryRecords(@NotNull RecordsQuery recordsQuery) throws Exception {

        RecsQueryRes<SectionRecord> result = new RecsQueryRes<>();

        if (recordsQuery.getLanguage().equals(PredicateService.LANGUAGE_PREDICATE)) {
            Predicate predicate = recordsQuery.getQuery(Predicate.class);

            RecordsQuery elementsQuery = recordsQuery.copy()
                    .withSourceId(ID)
                        .withLanguage(LANGUAGE_EMPTY)
                            .build();

            RawElements<EntityRef> elements = new RawElements(recordsService, new IterableRecordRefs(
                elementsQuery,
                IterableRecordsConfig.EMPTY,
                recordsService
            ));

            Set<String> filteredResultIds = predicateService.filter(elements, predicate).stream()
                .map(e -> e.getObj().getLocalId())
                .collect(Collectors.toSet());

            result.addRecords(sectionService.getAll(filteredResultIds).stream()
                .map(SectionRecord::new)
                .collect(Collectors.toList()));

        } else {
            result.setRecords(
                sectionService.getAll(
                    recordsQuery.getPage().getMaxItems(),
                    recordsQuery.getPage().getSkipCount()
                ).stream()
                .map(SectionRecord::new)
                .collect(Collectors.toList()));
            result.setTotalCount(sectionService.getCount());
        }
        return result;
    }

    @NotNull
    @Override
    public String getId() {
        return ID;
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
