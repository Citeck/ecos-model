package ru.citeck.ecos.model.dto;

import lombok.*;
import ru.citeck.ecos.records2.RecordRef;

@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
@ToString
//@JsonDeserialize(using = EcosTypeDtoDeserializer.class)
public class EcosTypeDto {

    @Getter @Setter private String uuid;

    @Getter @Setter private String name;

    @Getter @Setter private String description;

    @Getter @Setter private String tenant;

    @Getter @Setter private RecordRef parent;

    @Getter @Setter private RecordRef section;

    public EcosTypeDto(EcosTypeDto dto) {
        this.name = dto.name;
        this.description = dto.description;
        this.tenant = dto.tenant;
        this.parent = dto.parent;
        this.uuid = dto.uuid;
    }

    public EcosTypeDto(String uuid) {
        this.uuid = uuid;
    }

}
