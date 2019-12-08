package ru.citeck.ecos.model.dto;

import lombok.*;
import org.apache.logging.log4j.util.Strings;
import ru.citeck.ecos.apps.app.module.type.model.section.SectionModule;
import ru.citeck.ecos.model.dao.TypeRecordsDao;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.annotation.DisplayName;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class SectionDto {

    @Getter @Setter private String id;

    @Getter @Setter private String name;

    @Getter @Setter private String description;

    @Getter @Setter private String tenant;

    @Getter @Setter private Set<RecordRef> types;

    public SectionDto(SectionDto dto) {
        this.name = dto.name;
        this.description = dto.description;
        this.tenant = dto.tenant;
        this.id = dto.id;
        if (dto.types != null) {
            this.types = new HashSet<>(dto.types);
        }
    }

    public SectionDto(SectionModule module) {
        this.id = module.getId();
        this.name = module.getName();
        this.description = module.getDescription();
        this.tenant = Strings.EMPTY;
        if (module.getTypes() != null && !module.getTypes().isEmpty()) {
            this.types = module.getTypes().stream()
                .map(t -> RecordRef.create(TypeRecordsDao.ID, t.getId()))
                .collect(Collectors.toSet());
        }
    }

    @DisplayName
    public String getDisplayName() {
        return name;
    }
}
