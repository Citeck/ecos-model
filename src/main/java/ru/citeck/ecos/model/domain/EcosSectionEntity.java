package ru.citeck.ecos.model.domain;

import lombok.*;

import javax.persistence.*;
import java.util.Set;

@Entity
@Table(name = "ecos_section")
@AllArgsConstructor
@NoArgsConstructor
public class EcosSectionEntity {

    @Column(unique = true, nullable = false)
    @Getter @Setter private String extId;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Getter @Setter private Long id;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "section_type",
        joinColumns = { @JoinColumn(name = "section_id") },
        inverseJoinColumns = { @JoinColumn(name = "type_id") }
    )
    @Getter @Setter private Set<EcosTypeEntity> types;

    @NonNull
    @Getter @Setter private String name;

    @Getter @Setter private String description;

    @Getter @Setter private String tenant;


}
