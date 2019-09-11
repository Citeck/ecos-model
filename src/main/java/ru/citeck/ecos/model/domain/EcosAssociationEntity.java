package ru.citeck.ecos.model.domain;

import lombok.*;

import javax.persistence.*;

@Entity
@Table(name = "ecos_association")
@NoArgsConstructor
@AllArgsConstructor
public class EcosAssociationEntity {

    @Column(unique = true, nullable = false)
    @Getter
    @Setter
    private String extId;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Getter @Setter private Long id;

    @NonNull
    @Getter @Setter private String name;

    @Getter @Setter private String title;

    @ManyToOne
    @JoinColumn(name = "type_id")
    @Getter @Setter private EcosTypeEntity type;

}
