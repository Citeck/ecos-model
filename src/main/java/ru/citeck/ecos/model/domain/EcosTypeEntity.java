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

    @Column(name = "inherit_actions")
    private boolean inheritActions;

    @ManyToOne(cascade={CascadeType.DETACH})
    @JoinColumn(name="parent_id")
    private EcosTypeEntity parent;

    @OneToMany(mappedBy="parent", cascade = CascadeType.DETACH)
    private Set<EcosTypeEntity> childs = new HashSet<>();

    @ManyToMany(mappedBy = "types", fetch = FetchType.EAGER)
    private Set<EcosSectionEntity> sections = new HashSet<>();

    /*
     * Set of associations to this type
     */
    @OneToMany(mappedBy = "source", fetch = FetchType.EAGER)
    private Set<EcosAssociationEntity> assocsToThis = new HashSet<>();

    /*
     * Set of associations to other types
     */
    @OneToMany(mappedBy = "target", fetch = FetchType.EAGER)
    private Set<EcosAssociationEntity> assocsToOther = new HashSet<>();

    @OneToMany(mappedBy = "ecosType", cascade = {CascadeType.ALL}, fetch = FetchType.EAGER, orphanRemoval = true)
    private List<ActionEntity> actions = new ArrayList<>();

    public void addAction(ActionEntity actionEntity) {
        actions.add(actionEntity);
        actionEntity.setEcosType(this);
    }

    public void removeAction(ActionEntity actionEntity) {
        actions.remove(actionEntity);
        actionEntity.setEcosType(null);
    }

    public void setActions(List<ActionEntity> actionEntities) {
        throw new UnsupportedOperationException("You must use utility methods addAction/removeAction");
    }

}
