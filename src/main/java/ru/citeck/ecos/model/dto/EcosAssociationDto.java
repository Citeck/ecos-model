package ru.citeck.ecos.model.dto;

import lombok.*;
import ru.citeck.ecos.apps.app.module.type.type.association.AssociationDto;
import ru.citeck.ecos.records2.RecordRef;

@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class EcosAssociationDto {

    @Getter @Setter private String id;

    @Getter @Setter private String name;

    @Getter @Setter private String title;

    @Getter @Setter private RecordRef sourceType;

    @Getter @Setter private RecordRef targetType;

    public EcosAssociationDto(EcosAssociationDto dto) {
        this.id = dto.id;
        this.name = dto.name;
        this.title = dto.title;
        this.sourceType = dto.getSourceType();
        this.targetType = dto.getTargetType();
    }

    public EcosAssociationDto(AssociationDto deployDto, String sourceTypeId) {
        this.id = deployDto.getId();
        this.name = deployDto.getName();
        this.title = deployDto.getTitle();
        this.sourceType = RecordRef.create("type", sourceTypeId);
        this.targetType = RecordRef.create("type", deployDto.getTarget());
    }
}
