package ru.citeck.ecos.model.dto;

import lombok.*;
import ru.citeck.ecos.model.dao.TypeRecordsDao;
import ru.citeck.ecos.records2.RecordRef;

@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class AssociationDto {

    @Getter @Setter private String id;
    @Getter @Setter private String name;
    @Getter @Setter private RecordRef targetType;

    public AssociationDto(AssociationDto dto) {
        this.id = dto.id;
        this.name = dto.name;
        this.targetType = dto.getTargetType();
    }

    public AssociationDto(ru.citeck.ecos.apps.app.module.type.model.type.AssociationDto deployDto) {
        this.id = deployDto.getId();
        this.name = deployDto.getName();
        this.targetType = RecordRef.create(TypeRecordsDao.ID, deployDto.getTarget().getId());
    }
}
