package ru.citeck.ecos.model.dto;

import lombok.*;
import org.apache.commons.collections.CollectionUtils;
import org.apache.logging.log4j.util.Strings;
import ru.citeck.ecos.apps.app.module.type.type.TypeModule;
import ru.citeck.ecos.apps.app.module.type.type.action.ActionDto;
import ru.citeck.ecos.apps.app.module.type.type.association.AssociationDto;
import ru.citeck.ecos.model.dao.EcosTypeRecordsDao;
import ru.citeck.ecos.records2.RecordRef;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Getter
@Setter
public class EcosTypeDto {

    private String id;
    private String name;
    private String description;
    private String tenant;
    private RecordRef parent;
    private Set<EcosAssociationDto> associations = new HashSet<>();
    private Set<ActionDto> actions = new HashSet<>();
    private boolean inheritActions;

    public EcosTypeDto(EcosTypeDto dto) {
        this.name = dto.name;
        this.description = dto.description;
        this.tenant = dto.tenant;
        this.parent = dto.parent;
        this.id = dto.id;
        if (dto.associations != null) {
            this.associations = new HashSet<>(dto.associations);
        }
        if (dto.actions != null) {
            this.actions = new HashSet<>(dto.actions);
        }
        this.inheritActions = dto.isInheritActions();
    }

    public EcosTypeDto(TypeModule module) {
        this.id = module.getId();
        this.name = module.getName();
        this.description = module.getDescription();
        this.tenant = Strings.EMPTY;

        String parent = module.getParent();
        if (Strings.isNotBlank(parent)) {
            this.parent = RecordRef.create(EcosTypeRecordsDao.ID, parent);
        }

        List<AssociationDto> associations = module.getAssociations();
        if (CollectionUtils.isNotEmpty(associations)) {
            this.associations = associations
                .stream()
                .map(a -> new EcosAssociationDto(a, module.getId()))
                .collect(Collectors.toSet());
        }

        List<ActionDto> actions = module.getActions();
        if (CollectionUtils.isNotEmpty(associations)) {
            this.actions = new HashSet<>(actions);
        }
    }

    public EcosTypeDto(String id) {
        this.id = id;
    }

}
