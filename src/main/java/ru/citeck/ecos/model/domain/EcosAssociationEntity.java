package ru.citeck.ecos.model.domain;

import lombok.*;

import javax.persistence.*;

@Entity
@Table(name = "ecos_association")
@NoArgsConstructor
@AllArgsConstructor
public class EcosAssociationEntity {

    @Column(unique = true, nullable = false)
    @Getter @Setter private String extId;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Getter @Setter private Long id;

    @Getter @Setter private String name;

    @Getter @Setter private String title;

    @ManyToOne(cascade = CascadeType.DETACH)
    @JoinColumn(name = "source_id")
    @Getter @Setter EcosTypeEntity source;

    @ManyToOne(cascade = CascadeType.DETACH)
    @JoinColumn(name = "target_id")
    @Getter @Setter EcosTypeEntity target;

}
