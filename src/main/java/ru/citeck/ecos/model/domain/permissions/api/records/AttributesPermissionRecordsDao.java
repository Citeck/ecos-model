package ru.citeck.ecos.model.domain.permissions.api.records;

import ecos.com.fasterxml.jackson210.annotation.JsonIgnore;
import ecos.com.fasterxml.jackson210.annotation.JsonProperty;
import ecos.com.fasterxml.jackson210.annotation.JsonValue;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.domain.permissions.dto.AttributesPermissionDto;
import ru.citeck.ecos.model.domain.permissions.dto.AttributesPermissionWithMetaDto;
import ru.citeck.ecos.model.domain.permissions.service.AttributesPermissionsService;
import ru.citeck.ecos.model.utils.LegacyRecordsUtils;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.EmptyValue;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.records2.predicate.PredicateService;
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
public class AttributesPermissionRecordsDao extends LocalRecordsDao
    implements LocalRecordsQueryWithMetaDao<AttributesPermissionRecordsDao.AttributesPermissionRecord>,
    LocalRecordsMetaDao<MetaValue>,
    MutableRecordsLocalDao<AttributesPermissionRecordsDao.AttrPermissionsMutRecord> {

    private static final String ID = "attrs_permission";

    private final AttributesPermissionsService attributesPermissionsService;

    private final AttributesPermissionRecord EMPTY_RECORD = new AttributesPermissionRecord(new AttributesPermissionWithMetaDto());

    @Autowired
    public AttributesPermissionRecordsDao(AttributesPermissionsService attributesPermissionsService,
                                          PredicateService predicateService) {
        setId(ID);
        this.attributesPermissionsService = attributesPermissionsService;
        this.predicateService = predicateService;
    }

    @Override
    public List<MetaValue> getLocalRecordsMeta(List<RecordRef> list, MetaField metaField) {
        if (list.size() == 1 && list.get(0).getId().isEmpty()) {
            return Collections.singletonList(EMPTY_RECORD);
        }

        return list.stream()
            .map(ref -> {
                Optional<AttributesPermissionWithMetaDto> dto = attributesPermissionsService.getById(ref.getId());
                return dto.isPresent() ? new AttributesPermissionRecord(dto.get()) : EmptyValue.INSTANCE;
            })
            .collect(Collectors.toList());
    }

    @Override
    public RecordsDelResult delete(RecordsDeletion recordsDeletion) {

        List<RecordMeta> result = new ArrayList<>();

        recordsDeletion.getRecords().forEach(r -> {
            attributesPermissionsService.delete(r.getId());
            result.add(new RecordMeta(r));
        });

        RecordsDelResult delRes = new RecordsDelResult();
        delRes.setRecords(result);

        return delRes;
    }

    @Override
    public List<AttrPermissionsMutRecord> getValuesToMutate(List<RecordRef> list) {
        return list.stream()
            .map(RecordRef::getId)
            .map(id -> {
                if (StringUtils.isBlank(id)) {
                    return new AttrPermissionsMutRecord();
                } else {
                    return new AttrPermissionsMutRecord(attributesPermissionsService.getById(id).orElse(null));
                }
            })
            .collect(Collectors.toList());
    }

    @Override
    public RecordsMutResult save(List<AttrPermissionsMutRecord> list) {

        RecordsMutResult result = new RecordsMutResult();

        list.forEach(item -> {
            AttributesPermissionDto resDto = attributesPermissionsService.save(item);
            result.addRecord(new RecordMeta(RecordRef.valueOf(resDto.getId())));
        });

        return result;
    }

    @Override
    public RecordsQueryResult<AttributesPermissionRecord> queryLocalRecords(RecordsQuery recordsQuery, MetaField metaField) {

        RecordsQueryResult<AttributesPermissionRecord> result = new RecordsQueryResult<>();

        if (recordsQuery.getLanguage().equals(PredicateService.LANGUAGE_PREDICATE)) {
            Predicate predicate = recordsQuery.getQuery(Predicate.class);

            Collection<AttributesPermissionWithMetaDto> attrsPermWithMetas = attributesPermissionsService.getAll(
                recordsQuery.getMaxItems(),
                recordsQuery.getSkipCount(),
                predicate,
                LegacyRecordsUtils.mapLegacySortBy(recordsQuery.getSortBy())
            );

            result.setRecords(attrsPermWithMetas.stream()
                .map(AttributesPermissionRecord::new)
                .collect(Collectors.toList()));

            result.setTotalCount(attributesPermissionsService.getCount(predicate));

        } else {
            result.setRecords(attributesPermissionsService.getAll(recordsQuery.getMaxItems(), recordsQuery.getSkipCount())
                .stream()
                .map(AttributesPermissionRecord::new)
                .collect(Collectors.toList()));
            result.setTotalCount(attributesPermissionsService.getCount());
        }

        return result;
    }

    public static class AttributesPermissionRecord implements MetaValue {

        private final AttributesPermissionWithMetaDto dto;

        public AttributesPermissionRecord(AttributesPermissionWithMetaDto dto) {
            this.dto = dto;
        }

        @Override
        public Object getJson() {
            return dto;
        }

        @Override
        public String getId() {
            return dto.getId();
        }

        @Override
        @AttName("?disp")
        public String getDisplayName() {
            String dispName = dto.getId();
            if (dispName == null) {
                dispName = dto.getId();
            }
            return dispName;
        }

        @Override
        public Object getAttribute(String name, MetaField field) {
            switch (name) {
                case "extId":
                case "moduleId":
                    return dto.getId();
                case "typeRef":
                case RecordConstants.ATT_TYPE:
                    return dto.getTypeRef();
                case "rules":
                    return dto.getRules();
                case RecordConstants.ATT_MODIFIED:
                    return dto.getModified();
                case RecordConstants.ATT_MODIFIER:
                    return dto.getModifier(); //todo: return RecordRef of User
                case RecordConstants.ATT_CREATED:
                    return dto.getCreated();
                case RecordConstants.ATT_CREATOR:
                    return dto.getCreator();
            }
            return null;
        }

        @JsonIgnore
        public String getModuleId() {
            return getId();
        }
    }

    public static class AttrPermissionsMutRecord extends AttributesPermissionDto {

        public AttrPermissionsMutRecord() {
        }

        public AttrPermissionsMutRecord(AttributesPermissionDto dto) {
            super(dto);
        }

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
            return result != null ? result : "default_attrs_permission";
        }

        @JsonProperty("_content")
        public void setContent(List<ObjectData> content) {

            String dataUriContent = content.get(0).get("url", "");
            ObjectData data = Json.getMapper().read(dataUriContent, ObjectData.class);

            Json.getMapper().applyData(this, data);
        }

        @JsonValue
        public AttributesPermissionDto toJson() {
            return new AttributesPermissionDto(this);
        }
    }
}
