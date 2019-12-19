package ru.citeck.ecos.model.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.citeck.ecos.apps.app.module.type.model.type.AssocDirection;

import javax.persistence.*;

@Entity
@Table(name = "ecos_association")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssociationEntity {

    @Column(nullable = false)
    private String extId;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    private String name;

    @ManyToOne(cascade = CascadeType.DETACH)
    @JoinColumn(name = "target_id")
    private TypeEntity target;

    @Enumerated(EnumType.ORDINAL)
    private AssocDirection direction;
}
