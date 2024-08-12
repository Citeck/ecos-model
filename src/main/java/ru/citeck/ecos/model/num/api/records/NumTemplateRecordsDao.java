package ru.citeck.ecos.model.num.api.records;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
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
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao;
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao;
import ru.citeck.ecos.records3.record.dao.delete.DelStatus;
import ru.citeck.ecos.records3.record.dao.delete.RecordsDeleteDao;
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDtoDao;
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao;
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery;
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes;
import ru.citeck.ecos.webapp.api.entity.EntityRef;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class NumTemplateRecordsDao extends AbstractRecordsDao
    implements RecordAttsDao,
    RecordsQueryDao,
    RecordsDeleteDao,
    RecordMutateDtoDao<NumTemplateRecordsDao.NumTemplateRecord> {

    private static final String ID = "num-template";
    private final NumTemplateService numTemplateService;
    private final RecordEventsService recordEventsService;

    public NumTemplateRecordsDao(NumTemplateService numTemplateService, RecordEventsService recordEventsService) {
        this.numTemplateService = numTemplateService;
        this.recordEventsService = recordEventsService;

        numTemplateService.addListener(this::onTemplateChanged);
    }

    @Override
    public NumTemplateRecord getRecToMutate(@NotNull String recordId) throws Exception {
        return getRecordAtts(recordId);
    }

    @Nullable
    @Override
    public Object queryRecords(@NotNull RecordsQuery recordsQuery) throws Exception {

        RecsQueryRes<NumTemplateRecord> result = new RecsQueryRes<>();

        if ("predicate".equals(recordsQuery.getLanguage())) {

            Predicate predicate = recordsQuery.getQuery(Predicate.class);

            Collection<EntityWithMeta<NumTemplateDef>> types = numTemplateService.getAll(
                recordsQuery.getPage().getMaxItems(),
                recordsQuery.getPage().getSkipCount(),
                predicate,
                recordsQuery.getSortBy()
            );

            result.setRecords(types.stream()
                .map(NumTemplateRecord::new)
                .collect(Collectors.toList()));
            result.setTotalCount(numTemplateService.getCount(predicate));
            return result;
        }

        if ("criteria".equals(recordsQuery.getLanguage())) {

            result.setRecords(
                numTemplateService.getAll(
                    recordsQuery.getPage().getMaxItems(),
                    recordsQuery.getPage().getSkipCount()
                ).stream()
                .map(NumTemplateRecord::new)
                .collect(Collectors.toList())
            );
            result.setTotalCount(numTemplateService.getCount());

            return result;
        }

        return new RecsQueryRes<>();
    }

    @NotNull
    @Override
    public String saveMutatedRec(NumTemplateRecord dto) throws Exception {
        if (StringUtils.isBlank(dto.getId())) {
            throw new IllegalArgumentException("Attribute 'id' is mandatory");
        }
        return numTemplateService.save(dto).getEntity().getId();
    }

    @NotNull
    @Override
    public List<DelStatus> delete(@NotNull List<String> recordIds) throws Exception {
        List<DelStatus> results = new ArrayList<>();

        for (String recordId : recordIds) {
            numTemplateService.delete(recordId);
            results.add(DelStatus.OK);
        }
        return results;
    }

    @Nullable
    @Override
    public NumTemplateRecord getRecordAtts(@NotNull String recordId) throws Exception {

        NumTemplateWithMetaDto dto = numTemplateService.getById(recordId).map(NumTemplateWithMetaDto::new)
            .orElseGet(() -> new NumTemplateWithMetaDto(recordId));

        return new NumTemplateRecord(dto);
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

    @NotNull
    @Override
    public String getId() {
        return ID;
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
        public EntityRef getEcosType() {
            return EntityRef.create(EcosModelApp.NAME, "type", "number-template");
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
        public NumTemplateDto toJson() {
            return new NumTemplateDto(this);
        }

        public byte[] getData() {
            return YamlUtils.toNonDefaultString(toJson()).getBytes(StandardCharsets.UTF_8);
        }
    }
}
