package ru.citeck.ecos.model.section.domain;

import lombok.*;
import ru.citeck.ecos.model.domain.BaseEntity;
import ru.citeck.ecos.model.type.repository.TypeEntity;

import jakarta.persistence.*;
import java.util.Set;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Table(name = "ecos_section")
public class SectionEntity extends BaseEntity {

    @Column(unique = true, nullable = false)
    private String extId;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "section_type",
        joinColumns = {@JoinColumn(name = "section_id")},
        inverseJoinColumns = {@JoinColumn(name = "type_id")}
    )
    private Set<TypeEntity> types;

    @NonNull
    private String name;

    private String description;

    private String tenant;

    private String attributes;
}
