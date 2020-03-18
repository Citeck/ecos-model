package ru.citeck.ecos.model.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.citeck.ecos.model.eapps.listener.AssocDirection;

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

    @Id
    @Column(name = "source_id")
    private Long sourceId;

    private String name;

    @ManyToOne(cascade = CascadeType.DETACH)
    @JoinColumn(name = "source_id", insertable = false, updatable = false)
    private TypeEntity source;

    @ManyToOne(cascade = CascadeType.DETACH)
    @JoinColumn(name = "target_id")
    private TypeEntity target;

    @Enumerated(EnumType.ORDINAL)
    private AssocDirection direction;

    public AssociationId getId() {
        return new AssociationId(extId, sourceId);
    }
}
