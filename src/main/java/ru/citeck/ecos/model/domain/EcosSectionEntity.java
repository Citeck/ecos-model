package ru.citeck.ecos.model.domain;

import javax.persistence.*;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ecos_section")

@AllArgsConstructor
@NoArgsConstructor
public class EcosSectionEntity {

    @Getter
    @Setter
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @Getter
    @Setter
    private String name;

    @Getter
    @Setter
    private String decription;

    @Getter
    @Setter
    private String tenant;

}
