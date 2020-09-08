package ru.citeck.ecos.model.permissions.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.model.domain.AbstractAuditingEntity;
import ru.citeck.ecos.model.type.domain.TypeEntity;

import javax.persistence.*;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Table(name = "ecos_attrs_permission")
public class AttributesPermissionEntity extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_attrs_permission_seq_gen")
    @SequenceGenerator(name = "ecos_attrs_permission_seq_gen")
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
