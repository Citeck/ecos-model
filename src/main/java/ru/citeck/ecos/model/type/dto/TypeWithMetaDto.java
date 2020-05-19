package ru.citeck.ecos.model.type.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class TypeWithMetaDto extends TypeDto {

    private String modifier;
    private Instant modified;
    private String creator;
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
