package ru.citeck.ecos.model.domain;

import lombok.*;

import javax.persistence.*;
import java.util.Set;

@Entity
@Table(name = "ecos_section")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SectionEntity {

    @Column(unique = true, nullable = false)
    private String extId;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "section_type",
        joinColumns = { @JoinColumn(name = "section_id") },
        inverseJoinColumns = { @JoinColumn(name = "type_id") }
    )
    private Set<TypeEntity> types;

    @NonNull
    private String name;

    private String description;

    private String tenant;

    private String attributes;
}
