package ru.citeck.ecos.model.dto;

import lombok.*;
import ru.citeck.ecos.records2.RecordRef;

@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class AssociationDto {

    @Getter
    @Setter
    private String id;
    @Getter
    @Setter
    private String name;
    @Getter
    @Setter
    private RecordRef targetType;

    public AssociationDto(AssociationDto dto) {
        this.id = dto.id;
        this.name = dto.name;
        this.targetType = dto.getTargetType();
    }

}
