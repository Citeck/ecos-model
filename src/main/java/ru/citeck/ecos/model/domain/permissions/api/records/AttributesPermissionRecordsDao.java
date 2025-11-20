package ru.citeck.ecos.model.domain.permissions.api.records;

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
import ru.citeck.ecos.model.app.common.ModelSystemArtifactPerms;
import ru.citeck.ecos.model.domain.permissions.dto.AttributesPermissionDto;
import ru.citeck.ecos.model.domain.permissions.dto.AttributesPermissionWithMetaDto;
import ru.citeck.ecos.model.domain.permissions.service.AttributesPermissionsService;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records3.record.atts.value.AttValue;
import ru.citeck.ecos.records3.record.atts.value.impl.EmptyAttValue;
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao;
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao;
import ru.citeck.ecos.records3.record.dao.delete.DelStatus;
import ru.citeck.ecos.records3.record.dao.delete.RecordDeleteDao;
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDtoDao;
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao;
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery;
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName;
import ru.citeck.ecos.webapp.api.constants.AppName;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class AttributesPermissionRecordsDao extends AbstractRecordsDao
    implements RecordsQueryDao,
    RecordAttsDao,
    RecordDeleteDao,
    RecordMutateDtoDao<AttributesPermissionRecordsDao.AttrPermissionsMutRecord> {

    public static final String ID = "attrs_permission";

    private final AttributesPermissionsService attributesPermissionsService;
    private final ModelSystemArtifactPerms perms;

    private final AttributesPermissionRecord EMPTY_RECORD = new AttributesPermissionRecord(new AttributesPermissionWithMetaDto());

    @Autowired
    public AttributesPermissionRecordsDao(AttributesPermissionsService attributesPermissionsService,
                                          ModelSystemArtifactPerms perms) {
        this.attributesPermissionsService = attributesPermissionsService;
        this.perms = perms;
    }

    @Nullable
    @Override
    public Object getRecordAtts(@NotNull String recordId) throws Exception {
        if (recordId.isEmpty()) {
            return Collections.singletonList(EMPTY_RECORD);
        }
        AttributesPermissionWithMetaDto perms = attributesPermissionsService.getById(recordId).orElse(null);
        if (perms != null) {
            return new AttributesPermissionRecord(perms);
        } else {
            return EmptyAttValue.INSTANCE;
        }
    }

    @NotNull
    @Override
    public DelStatus delete(@NotNull String recordId) throws Exception {
        attributesPermissionsService.delete(recordId);
        return DelStatus.OK;
    }

    @Override
    public AttrPermissionsMutRecord getRecToMutate(@NotNull String recordId) {
        if (StringUtils.isBlank(recordId)) {
            return new AttrPermissionsMutRecord();
        } else {
            return new AttrPermissionsMutRecord(attributesPermissionsService.getById(recordId).orElse(null));
        }
    }

    @NotNull
    @Override
    public String saveMutatedRec(AttrPermissionsMutRecord attrPermissionsMutRecord) throws Exception {
        return attributesPermissionsService.save(attrPermissionsMutRecord).getId();
    }

    @Nullable
    @Override
    public Object queryRecords(@NotNull RecordsQuery recordsQuery) throws Exception {

        RecsQueryRes<AttributesPermissionRecord> result = new RecsQueryRes<>();

        if (recordsQuery.getLanguage().equals(PredicateService.LANGUAGE_PREDICATE)) {
            Predicate predicate = recordsQuery.getQuery(Predicate.class);

            Collection<AttributesPermissionWithMetaDto> attrsPermWithMetas = attributesPermissionsService.getAll(
                recordsQuery.getPage().getMaxItems(),
                recordsQuery.getPage().getSkipCount(),
                predicate,
                recordsQuery.getSortBy()
            );

            result.setRecords(attrsPermWithMetas.stream()
                .map(AttributesPermissionRecord::new)
                .collect(Collectors.toList()));

            result.setTotalCount(attributesPermissionsService.getCount(predicate));

        } else {
            result.setRecords(
                attributesPermissionsService.getAll(
                        recordsQuery.getPage().getMaxItems(),
                        recordsQuery.getPage().getSkipCount()
                    )
                    .stream()
                    .map(AttributesPermissionRecord::new)
                    .collect(Collectors.toList()));
            result.setTotalCount(attributesPermissionsService.getCount());
        }

        return result;
    }

    @NotNull
    @Override
    public String getId() {
        return ID;
    }

    public class AttributesPermissionRecord implements AttValue {

        private final AttributesPermissionWithMetaDto dto;

        public AttributesPermissionRecord(AttributesPermissionWithMetaDto dto) {
            this.dto = dto;
        }

        @Override
        public Object asJson() {
            return dto;
        }

        @Override
        public String getId() {
            return dto.getId();
        }

        @Override
        @AttName("?disp")
        public String getDisplayName() {
            return dto.getId();
        }

        @Override
        public Object getAtt(String name) {
            return switch (name) {
                case "extId", "moduleId" -> dto.getId();
                case "typeRef", RecordConstants.ATT_TYPE -> dto.getTypeRef();
                case "rules" -> dto.getRules();
                case "permissions" -> perms.getPerms(EntityRef.create(AppName.EMODEL, ID, getId()));
                case RecordConstants.ATT_MODIFIED -> dto.getModified();
                case RecordConstants.ATT_MODIFIER -> dto.getModifier(); //todo: return RecordRef of User
                case RecordConstants.ATT_CREATED -> dto.getCreated();
                case RecordConstants.ATT_CREATOR -> dto.getCreator();
                default -> null;
            };
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
            return getId();
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
