package ru.citeck.ecos.model.domain.perms.repo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import ru.citeck.ecos.model.domain.AbstractAuditingEntity;

import javax.persistence.*;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Table(name = "type_permissions")
public class TypePermsEntity extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "type_permissions_seq_gen")
    @SequenceGenerator(name = "type_permissions_seq_gen")
    private Long id;

    private String typeRef;
    private String extId;
    private String permissions;
    private String attributes;
}
