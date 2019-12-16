package ru.citeck.ecos.model.dto;

import lombok.*;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.annotation.DisplayName;

import java.util.HashSet;
import java.util.Set;

@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class SectionDto {

    @Getter
    @Setter
    private String id;

    @Getter
    @Setter
    private String name;

    @Getter
    @Setter
    private String description;

    @Getter
    @Setter
    private String tenant;

    @Getter
    @Setter
    private Set<RecordRef> types;

    public SectionDto(SectionDto dto) {
        this.name = dto.name;
        this.description = dto.description;
        this.tenant = dto.tenant;
        this.id = dto.id;
        if (dto.types != null) {
            this.types = new HashSet<>(dto.types);
        }
    }

    @DisplayName
    public String getDisplayName() {
        return name;
    }
}
