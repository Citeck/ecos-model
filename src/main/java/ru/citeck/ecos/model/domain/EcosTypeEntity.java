package ru.citeck.ecos.model.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "ecos_type")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class EcosTypeEntity {

    @Column(unique = true, nullable = false)
    private String extId;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    private String tenant;

    @ManyToOne(cascade={CascadeType.DETACH})
    @JoinColumn(name="parent_id")
    private EcosTypeEntity parent;

    @OneToMany(mappedBy="parent", cascade = CascadeType.DETACH)
    private Set<EcosTypeEntity> childs = new HashSet<>();

    @ManyToMany(mappedBy = "types", fetch = FetchType.EAGER)
    private Set<EcosSectionEntity> sections;

    /*
     * Set of associations to this type
     */
    @OneToMany(mappedBy = "source", fetch = FetchType.EAGER)
    private Set<EcosAssociationEntity> assocsToThis;

    /*
     * Set of associations to other types
     */
    @OneToMany(mappedBy = "target", fetch = FetchType.EAGER)
    private Set<EcosAssociationEntity> assocsToOther;

    @OneToMany(mappedBy = "ecosType", cascade = {CascadeType.ALL}, fetch = FetchType.EAGER, orphanRemoval = true)
    private List<ActionEntity> actions = new ArrayList<>();

}
