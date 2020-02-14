package ru.citeck.ecos.model.dto;

import ecos.com.fasterxml.jackson210.databind.node.ObjectNode;
import lombok.*;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.annotation.DisplayName;

import java.util.HashSet;
import java.util.Set;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class SectionDto {

    private String id;
    private String name;
    private String description;
    private String tenant;
    private Set<RecordRef> types;

    private ObjectNode attributes;

    public SectionDto(SectionDto dto) {
        this.name = dto.name;
        this.description = dto.description;
        this.tenant = dto.tenant;
        this.id = dto.id;
        this.attributes = dto.attributes != null ? dto.attributes.deepCopy() : null;
        if (dto.types != null) {
            this.types = new HashSet<>(dto.types);
        }
    }

    @DisplayName
    public String getDisplayName() {
        return name;
    }
}
