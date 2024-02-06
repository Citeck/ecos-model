package ru.citeck.ecos.model.num.api.records;

import ecos.com.fasterxml.jackson210.annotation.JsonProperty;
import ecos.com.fasterxml.jackson210.annotation.JsonValue;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.data.entity.EntityWithMeta;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.commons.json.YamlUtils;
import ru.citeck.ecos.commons.utils.TmplUtils;
import ru.citeck.ecos.events2.type.RecordEventsService;
import ru.citeck.ecos.model.EcosModelApp;
import ru.citeck.ecos.model.lib.num.dto.NumTemplateDef;
import ru.citeck.ecos.model.num.dto.NumTemplateDto;
import ru.citeck.ecos.model.num.dto.NumTemplateWithMetaDto;
import ru.citeck.ecos.model.num.service.NumTemplateService;
import ru.citeck.ecos.model.utils.LegacyRecordsUtils;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
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
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName;

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

        if ("predicate".equals(recordsQuery.getLanguage())) {

            Predicate predicate = recordsQuery.getQuery(Predicate.class);

            Collection<EntityWithMeta<NumTemplateDef>> types = numTemplateService.getAll(
                recordsQuery.getMaxItems(),
                recordsQuery.getSkipCount(),
                predicate,
                LegacyRecordsUtils.mapLegacySortBy(recordsQuery.getSortBy())
            );

            result.setRecords(types.stream()
                .map(NumTemplateRecord::new)
                .collect(Collectors.toList()));
            result.setTotalCount(numTemplateService.getCount(predicate));
            return result;
        }

        if ("criteria".equals(recordsQuery.getLanguage())) {

            result.setRecords(numTemplateService.getAll(recordsQuery.getMaxItems(), recordsQuery.getSkipCount())
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
                throw new IllegalArgumentException("Attribute 'id' is mandatory");
            }
            String id = numTemplateService.save(dto).getEntity().getId();
            result.addRecord(new RecordMeta(id));
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
            .map(id -> numTemplateService.getById(id).map(NumTemplateWithMetaDto::new)
                .orElseGet(() -> new NumTemplateWithMetaDto(id)))
            .map(NumTemplateRecord::new)
            .collect(Collectors.toList());
    }

    private void onTemplateChanged(@Nullable EntityWithMeta<NumTemplateDef> before,
                                   @Nullable EntityWithMeta<NumTemplateDef> after) {
        if (after != null) {
            recordEventsService.emitRecChanged(
                before,
                after,
                getId(),
                dto -> new NumTemplateRecord(new NumTemplateWithMetaDto(dto))
            );
        }
    }

    @NoArgsConstructor
    public static class NumTemplateRecord extends NumTemplateWithMetaDto {

        @Getter @Setter
        private List<String> modelAttributes;

        public NumTemplateRecord(NumTemplateWithMetaDto model) {
            super(model);
            modelAttributes = new ArrayList<>(TmplUtils.getAtts(this.getCounterKey()));
        }

        public NumTemplateRecord(EntityWithMeta<NumTemplateDef> model) {
            super(model);
            modelAttributes = model.getEntity().getModelAttributes();
        }

        @AttName("_type")
        public RecordRef getEcosType() {
            return RecordRef.create(EcosModelApp.NAME, "type", "number-template");
        }

        public String getModuleId() {
            return getId();
        }

        public void setModuleId(String value) {
            setId(value);
        }

        @AttName("?disp")
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
