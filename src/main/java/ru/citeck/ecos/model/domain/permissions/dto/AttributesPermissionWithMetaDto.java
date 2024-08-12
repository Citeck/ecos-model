package ru.citeck.ecos.model.domain.permissions.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.Instant;

@Data
public class AttributesPermissionWithMetaDto extends AttributesPermissionDto {

    @JsonIgnore
    private String modifier;
    @JsonIgnore
    private Instant modified;
    @JsonIgnore
    private String creator;
    @JsonIgnore
    private Instant created;

    public AttributesPermissionWithMetaDto() {
    }

    public AttributesPermissionWithMetaDto(AttributesPermissionDto other) {
        super(other);
    }

    public AttributesPermissionWithMetaDto(AttributesPermissionWithMetaDto other) {
        super(other);

        this.modifier = other.modifier;
        this.modified = other.modified;
        this.creator = other.creator;
        this.created = other.created;
    }
}
