package ru.citeck.ecos.model.dto;

import lombok.*;

import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;

@ToString
@AllArgsConstructor
@NoArgsConstructor
public class EcosTypeDto {


    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Getter @Setter private Long id;

    @Getter @Setter private String name;

    @Getter @Setter private String decription;

    @Getter @Setter private String tenant;

    public EcosTypeDto(EcosTypeDto dto) {
        this.name = dto.name;
        this.decription = dto.decription;
        this.tenant = dto.tenant;
    }
}
