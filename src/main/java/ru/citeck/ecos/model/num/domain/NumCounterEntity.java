package ru.citeck.ecos.model.num.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "ecos_num_counter")
public class NumCounterEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_num_counter_seq_gen")
    @SequenceGenerator(name = "ecos_num_counter_seq_gen")
    private Long id;

    @Column(nullable = false)
    private String key;

    private Long counter;

    @ManyToOne(cascade = {CascadeType.DETACH})
    @JoinColumn(name = "num_template_id")
    private NumTemplateEntity template;
}
