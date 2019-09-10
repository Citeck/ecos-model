package ru.citeck.ecos.model.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "ecos_type")
@AllArgsConstructor
@NoArgsConstructor
public class EcosTypeEntity {

    @Column(unique = true, nullable = false)
    @Getter @Setter private String extId;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Getter @Setter private Long id;

    @Column(nullable = false)
    @Getter @Setter private String name;

    @Getter @Setter private String description;

    @Getter @Setter private String tenant;

    @ManyToOne(cascade={CascadeType.DETACH})
    @JoinColumn(name="parent_id")
    @Getter @Setter private EcosTypeEntity parent;

    @OneToMany(mappedBy="parent", cascade = CascadeType.DETACH)
    @Getter @Setter private Set<EcosTypeEntity> childs = new HashSet<>();

    @ManyToMany(mappedBy = "types", fetch = FetchType.EAGER)
    @Getter @Setter private Set<EcosSectionEntity> sections;

}
