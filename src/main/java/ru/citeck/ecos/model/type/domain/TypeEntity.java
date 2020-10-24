package ru.citeck.ecos.model.type.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import ru.citeck.ecos.model.association.domain.AssociationEntity;
import ru.citeck.ecos.model.domain.AbstractAuditingEntity;
import ru.citeck.ecos.model.section.domain.SectionEntity;
import ru.citeck.ecos.model.utils.EntityCollectionUtils;

import javax.persistence.*;
import java.util.*;
import java.util.stream.Collectors;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Table(name = "ecos_type")
public class TypeEntity extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_types_seq_gen")
    @SequenceGenerator(name = "ecos_types_seq_gen")
    private Long id;

    @Column(unique = true, nullable = false)
    private String extId;

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

    @Column(name = "disp_name_template")
    private String dispNameTemplate;

    @Column(name = "num_template_ref")
    private String numTemplateRef;

    @Column(name = "inherit_num_template")
    private Boolean inheritNumTemplate;

    @Column(name = "inherit_form")
    private Boolean inheritForm;

    @Column(name = "computed_attributes")
    private String computedAttributes;

    @Column(name = "source_id")
    private String sourceId;

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
     * Set of associations to other types.
     */
    @OneToMany(
        mappedBy = "source",
        fetch = FetchType.EAGER,
        cascade = CascadeType.ALL,
        orphanRemoval = true)
    private Set<AssociationEntity> associations = new HashSet<>();

    @Column(name = "actions_str")
    private String actions;

    private String attributeDefs;

    private String roles;

    private String statuses;

    public void setAssociations(Set<AssociationEntity> associations) {
        associations = associations.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        EntityCollectionUtils.changeHibernateSet(this.associations, associations, AssociationEntity::getId);
    }

    public void setId(Long id) {
        this.id = id;
    }

}
