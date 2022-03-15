package ru.citeck.ecos.model.num.records;

import ecos.com.fasterxml.jackson210.annotation.JsonProperty;
import ecos.com.fasterxml.jackson210.annotation.JsonValue;
import kotlin.Unit;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.commons.json.YamlUtils;
import ru.citeck.ecos.commons.utils.TmplUtils;
import ru.citeck.ecos.events2.type.RecordEventsService;
import ru.citeck.ecos.model.num.dto.NumTemplateDto;
import ru.citeck.ecos.model.num.dto.NumTemplateWithMetaDto;
import ru.citeck.ecos.model.num.service.NumTemplateService;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
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
import ru.citeck.ecos.records3.record.atts.schema.resolver.AttSchemaResolver;
import ru.citeck.ecos.records3.record.request.RequestContext;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class NumTemplateRecordsDao extends LocalRecordsDao
    implements LocalRecordsMetaDao<NumTemplateRecordsDao.NumTemplateRecord>,
    LocalRecordsQueryWithMetaDao<NumTemplateRecordsDao.NumTemplateRecord>,
    MutableRecordsLocalDao<NumTemplateRecordsDao.NumTemplateRecord> {

    private static final String ID = "num-template";
    private final NumTemplateService numTemplateService;
    private final RecordEventsService recordEventsService;

    public NumTemplateRecordsDao(NumTemplateService numTemplateService, RecordEventsService recordEventsService) {
        setId(ID);
        this.numTemplateService = numTemplateService;
        this.recordEventsService = recordEventsService;

        numTemplateService.addListener(this::onTemplateChanged);
    }

    @Override
    public List<NumTemplateRecord> getValuesToMutate(List<RecordRef> records) {
        return getLocalRecordsMeta(records, null);
    }

    @Override
    public RecordsQueryResult<NumTemplateRecord> queryLocalRecords(RecordsQuery recordsQuery, MetaField metaField) {

        RecordsQueryResult<NumTemplateRecord> result = new RecordsQueryResult<>();
        int max = recordsQuery.getMaxItems();
        if (max <= 0) {
            max = 10000;
        }
        int skip = recordsQuery.getSkipCount();

        if ("predicate".equals(recordsQuery.getLanguage())) {

            Predicate predicate = recordsQuery.getQuery(Predicate.class);

            List<Sort.Order> order = recordsQuery.getSortBy()
                .stream()
                .filter(s -> RecordConstants.ATT_MODIFIED.equals(s.getAttribute()))
                .map(s -> {
                    String attribute = s.getAttribute();
                    if (RecordConstants.ATT_MODIFIED.equals(attribute)) {
                        attribute = "lastModifiedDate";
                    }
                    return s.isAscending() ? Sort.Order.asc(attribute) : Sort.Order.desc(attribute);
                })
                .collect(Collectors.toList());

            Collection<NumTemplateWithMetaDto> types = numTemplateService.getAll(
                max,
                recordsQuery.getSkipCount(),
                predicate,
                !order.isEmpty() ? Sort.by(order) : null
            );

            result.setRecords(types.stream()
                .map(NumTemplateRecord::new)
                .collect(Collectors.toList()));
            result.setTotalCount(numTemplateService.getCount(predicate));
            return result;
        }

        if ("criteria".equals(recordsQuery.getLanguage())) {

            result.setRecords(numTemplateService.getAll(max, skip)
                .stream()
                .map(NumTemplateRecord::new)
                .collect(Collectors.toList())
            );
            result.setTotalCount(numTemplateService.getCount());

            return result;
        }

        return new RecordsQueryResult<>();
    }

    @Override
    public RecordsMutResult save(List<NumTemplateRecord> values) {

        RecordsMutResult result = new RecordsMutResult();
        values.forEach(dto -> {
            if (StringUtils.isBlank(dto.getId())) {
                throw new IllegalArgumentException("Parameter 'id' is mandatory for menu record");
            }
            NumTemplateDto saved = numTemplateService.save(dto);
            result.addRecord(new RecordMeta(saved.getId()));
        });

        return result;
    }

    @Override
    public RecordsDelResult delete(RecordsDeletion deletion) {
        RecordsDelResult result = new RecordsDelResult();
        deletion.getRecords().stream()
            .map(RecordRef::getId)
            .forEach(id -> {
                numTemplateService.delete(id);
                result.addRecord(new RecordMeta(id));
            });
        return result;
    }

    @Override
    public List<NumTemplateRecord> getLocalRecordsMeta(List<RecordRef> records, MetaField metaField) {
        return records.stream()
            .map(RecordRef::getId)
            .map(id -> numTemplateService.getById(id)
                .orElseGet(() -> new NumTemplateWithMetaDto(id)))
            .map(NumTemplateRecord::new)
            .collect(Collectors.toList());
    }

    private void onTemplateChanged(@Nullable NumTemplateDto before, NumTemplateDto after) {
        recordEventsService.emitRecChanged(
            before,
            after,
            getId(),
            dto -> new NumTemplateRecord(new NumTemplateWithMetaDto(dto))
        );
    }

    @NoArgsConstructor
    public static class NumTemplateRecord extends NumTemplateWithMetaDto {

        @Getter @Setter
        private List<String> modelAttributes;

        public NumTemplateRecord(NumTemplateWithMetaDto model) {
            super(model);
            modelAttributes = new ArrayList<>(TmplUtils.getAtts(this.getCounterKey()));
        }

        @MetaAtt(".type")
        public RecordRef getEcosType() {
            return RecordRef.create("emodel", "type", "number-template");
        }

        public String getModuleId() {
            return getId();
        }

        public void setModuleId(String value) {
            setId(value);
        }

        @MetaAtt(".disp")
        public String getDisplayName() {
            return getId();
        }

        @JsonProperty("_content")
        public void setContent(List<ObjectData> content) {

            String dataUriContent = content.get(0).get("url", "");
            ObjectData data = Json.getMapper().read(dataUriContent, ObjectData.class);

            Json.getMapper().applyData(this, data);
        }

        @JsonValue
        @com.fasterxml.jackson.annotation.JsonValue
        public NumTemplateDto toJson() {
            return new NumTemplateDto(this);
        }

        public byte[] getData() {
            return YamlUtils.toNonDefaultString(toJson()).getBytes(StandardCharsets.UTF_8);
        }
    }
}
