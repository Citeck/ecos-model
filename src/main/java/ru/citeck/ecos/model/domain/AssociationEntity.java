package ru.citeck.ecos.model.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.citeck.ecos.apps.app.module.type.model.type.AssocDirection;

import javax.persistence.*;

@Entity
@Table(name = "ecos_association")
@IdClass(AssociationId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssociationEntity {

    @Id
    @Column(nullable = false)
    private String extId;

    private String name;

    @Id
    @ManyToOne(cascade = CascadeType.DETACH)
    @JoinColumn(name = "source_id")
    private TypeEntity source;

    @ManyToOne(cascade = CascadeType.DETACH)
    @JoinColumn(name = "target_id")
    private TypeEntity target;

    @Enumerated(EnumType.ORDINAL)
    private AssocDirection direction;
}
