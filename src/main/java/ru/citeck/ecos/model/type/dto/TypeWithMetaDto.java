package ru.citeck.ecos.model.type.dto;

import ecos.com.fasterxml.jackson210.annotation.JsonIgnore;
import lombok.Data;

import java.time.Instant;

@Data
public class TypeWithMetaDto extends TypeDto {

    @JsonIgnore
    private String modifier;
    @JsonIgnore
    private Instant modified;
    @JsonIgnore
    private String creator;
    @JsonIgnore
    private Instant created;

    public TypeWithMetaDto() {
    }

    public TypeWithMetaDto(TypeDto other) {
        super(other);
    }

    public TypeWithMetaDto(TypeWithMetaDto other) {
        super(other);

        this.modifier = other.modifier;
        this.modified = other.modified;
        this.creator = other.creator;
        this.created = other.created;
    }
}
