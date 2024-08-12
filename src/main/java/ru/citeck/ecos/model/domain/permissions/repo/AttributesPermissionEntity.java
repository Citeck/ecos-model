package ru.citeck.ecos.model.domain.permissions.repo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import ru.citeck.ecos.model.domain.AbstractAuditingEntity;
import ru.citeck.ecos.model.type.repository.TypeEntity;

import jakarta.persistence.*;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Table(name = "ecos_attrs_permission")
public class AttributesPermissionEntity extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hibernate_sequence")
    @SequenceGenerator(name = "hibernate_sequence")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "type_id")
    private TypeEntity type;

    @Column(name = "ext_id")
    private String extId;

    @Lob
    @Column(name = "rules_str")
    private String rules;

}
