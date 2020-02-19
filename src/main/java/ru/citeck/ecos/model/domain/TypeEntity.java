package ru.citeck.ecos.model.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.citeck.ecos.model.utils.EntityCollectionUtils;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "ecos_type")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TypeEntity {

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

    private String form;

    private String attributes;

    private Boolean system;

    private String dashboardType;

    @Column(name = "create_variants")
    private String createVariants;

    @Column(name = "inherit_actions")
    private boolean inheritActions;

    @ManyToOne(cascade={CascadeType.DETACH})
    @JoinColumn(name="parent_id")
    private TypeEntity parent;

    @OneToMany(mappedBy="parent", cascade = CascadeType.DETACH)
    private Set<TypeEntity> children = new HashSet<>();

    @ManyToMany(mappedBy = "types", fetch = FetchType.EAGER)
    private Set<SectionEntity> sections = new HashSet<>();

    /*
     * Set of associations to this type
     */
    @OneToMany(
        mappedBy = "target",
        fetch = FetchType.EAGER,
        cascade = CascadeType.ALL)
    private Set<AssociationEntity> assocsToThis = new HashSet<>();;

    /*
     * Set of associations to other types
     */
    @OneToMany(
        mappedBy = "source",
        fetch = FetchType.EAGER,
        cascade = CascadeType.ALL,
        orphanRemoval=true)
    private Set<AssociationEntity> assocsToOthers = new HashSet<>();

    @OneToMany(
        mappedBy = "type",
        cascade = {CascadeType.ALL},
        fetch = FetchType.EAGER,
        orphanRemoval = true)
    private List<TypeActionEntity> actions = new ArrayList<>();

    public void setAssocsToOthers(Set<AssociationEntity> assocsToOthers) {
        EntityCollectionUtils.changeHibernateSet(this.assocsToOthers, assocsToOthers, AssociationEntity::getId);
    }

    public void addAction(TypeActionEntity actionEntity) {
        actions.add(actionEntity);
    }

    public void addActions(List<TypeActionEntity> actions) {
        actions.forEach(this::addAction);
    }

    public void removeAction(TypeActionEntity actionEntity) {
        actions.remove(actionEntity);
        actionEntity.setType(null);
    }

    public void setActions(List<TypeActionEntity> actionEntities) {
        throw new UnsupportedOperationException("You must use utility methods addAction/removeAction");
    }
}
