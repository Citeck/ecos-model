package ru.citeck.ecos.model.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Table(name = "ecos_type")
@AllArgsConstructor
@NoArgsConstructor
public class EcosTypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Getter @Setter private Long id;

    @Getter @Setter private String name;

    @Getter @Setter private String decription;

    @Getter @Setter private String tenant;

}
