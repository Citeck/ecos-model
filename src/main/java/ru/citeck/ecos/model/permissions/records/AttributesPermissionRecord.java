package ru.citeck.ecos.model.permissions.records;

import ecos.com.fasterxml.jackson210.annotation.JsonIgnore;
import ecos.com.fasterxml.jackson210.annotation.JsonProperty;
import ecos.com.fasterxml.jackson210.annotation.JsonValue;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.permissions.dto.AttributesPermissionWithMetaDto;
import ru.citeck.ecos.model.permissions.dto.AttributesPermissionDto;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;

import java.util.Base64;
import java.util.List;

public class AttributesPermissionRecord implements MetaValue {

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
    @MetaAtt(".disp")
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
        @MetaAtt(".disp")
        public String getDisplayName() {
            String result = getId();
            return result != null ? result : "default_attrs_permission";
        }

        @JsonProperty("_content")
        public void setContent(List<ObjectData> content) {

            String base64Content = content.get(0).get("url", "");
            base64Content = base64Content.replaceAll("^data:application/json;base64,", "");
            ObjectData data = Json.getMapper().read(Base64.getDecoder().decode(base64Content), ObjectData.class);

            Json.getMapper().applyData(this, data);
        }

        @JsonValue
        public AttributesPermissionDto toJson() {
            return new AttributesPermissionDto(this);
        }
    }
}
