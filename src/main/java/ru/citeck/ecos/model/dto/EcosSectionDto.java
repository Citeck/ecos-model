package ru.citeck.ecos.model.dto;

import lombok.*;
import org.apache.logging.log4j.util.Strings;
import ru.citeck.ecos.apps.app.module.type.section.SectionModule;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.annotation.DisplayName;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class EcosSectionDto {

    @Getter @Setter private String id;

    @Getter @Setter private String name;

    @Getter @Setter private String description;

    @Getter @Setter private String tenant;

    @Getter @Setter private Set<RecordRef> types;

    public EcosSectionDto(EcosSectionDto dto) {
        this.name = dto.name;
        this.description = dto.description;
        this.tenant = dto.tenant;
        this.id = dto.id;
        if (dto.types != null) {
            this.types = new HashSet<>(dto.types);
        }
    }

    public EcosSectionDto(SectionModule module) {
        this.id = module.getId();
        this.name = module.getName();
        this.description = module.getDescription();
        this.tenant = Strings.EMPTY;
        if (module.getTypes() != null && !module.getTypes().isEmpty()) {
            this.types = module.getTypes().stream()
                .map(t -> RecordRef.create("type", t))
                .collect(Collectors.toSet());
        }
    }

    @DisplayName
    public String getDisplayName() {
        return name;
    }
}
