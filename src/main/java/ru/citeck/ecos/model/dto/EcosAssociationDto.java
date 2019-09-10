package ru.citeck.ecos.model.dto;


import lombok.*;
import ru.citeck.ecos.records2.RecordRef;

@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class EcosAssociationDto {


    @Getter
    @Setter private String extId;

    @Getter @Setter private String name;

    @Getter @Setter private String title;

    @Getter @Setter private RecordRef type;

    public EcosAssociationDto(EcosAssociationDto dto) {
        this.extId = dto.extId;
        this.name = dto.name;
        this.title = dto.title;
        this.type = dto.type;
    }
}
