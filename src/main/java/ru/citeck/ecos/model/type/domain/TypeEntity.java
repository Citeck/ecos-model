package ru.citeck.ecos.model.type.domain;

import lombok.Data;
import ru.citeck.ecos.model.association.domain.AssociationEntity;
import ru.citeck.ecos.model.section.domain.SectionEntity;
import ru.citeck.ecos.model.utils.EntityCollectionUtils;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Data
@Entity
@Table(name = "ecos_type")
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

    private String journal;

    private String attributes;

    private Boolean system;

    private String dashboardType;

    private String configForm;

    private String config;

    @Column(name = "create_variants")
    private String createVariants;

    @Column(name = "inherit_actions")
    private boolean inheritActions;

    @ManyToOne(cascade = {CascadeType.DETACH})
    @JoinColumn(name = "parent_id")
    private TypeEntity parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.DETACH)
    private Set<TypeEntity> children = new HashSet<>();

    @ManyToMany(mappedBy = "types", fetch = FetchType.EAGER)
    private Set<SectionEntity> sections = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ecos_type_alias", joinColumns = @JoinColumn(name = "type_id"))
    @Column(name = "alias")
    private Set<String> aliases = new HashSet<>();

    /*
     * Set of associations to this type
     */
    @OneToMany(
        mappedBy = "target",
        fetch = FetchType.EAGER,
        cascade = CascadeType.ALL)
    private Set<AssociationEntity> assocsToThis = new HashSet<>();

    /*
     * Set of associations to other types
     */
    @OneToMany(
        mappedBy = "source",
        fetch = FetchType.EAGER,
        cascade = CascadeType.ALL,
        orphanRemoval = true)
    private Set<AssociationEntity> assocsToOthers = new HashSet<>();

    @Column(name = "actions_str")
    private String actions;

    public void setAssocsToOthers(Set<AssociationEntity> assocsToOthers) {
        EntityCollectionUtils.changeHibernateSet(this.assocsToOthers, assocsToOthers, AssociationEntity::getId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypeEntity that = (TypeEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return 31;
    }
}
