package ru.citeck.ecos.model.num.domain;

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
@Table(name = "ecos_num_template")
public class NumTemplateEntity extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_num_template_seq_gen")
    @SequenceGenerator(name = "ecos_num_template_seq_gen")
    private Long id;

    @Column(unique = true, nullable = false)
    private String extId;

    @Column(nullable = false)
    private String name;

    @Column(name = "counter_key", nullable = false)
    private String counterKey;
}
