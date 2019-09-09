package ru.citeck.ecos.model.dto;

import lombok.*;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.annotation.DisplayName;

import java.util.Set;

@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class EcosSectionDto {

    @Getter @Setter private String uuid;

    @Getter @Setter private String name;

    @Getter @Setter private String description;

    @Getter @Setter private String tenant;

    @Getter @Setter private Set<RecordRef> types;

    public EcosSectionDto(EcosSectionDto dto) {
        this.name = dto.name;
        this.description = dto.description;
        this.tenant = dto.tenant;
        this.uuid = dto.uuid;
        this.types = dto.types;
    }

    @DisplayName
    public String getDisplayName() {
        return name;
    }
}
