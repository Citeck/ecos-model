package ru.citeck.ecos.model.num.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.io.Serializable;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "ecos_num_counter")
public class NumCounterEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hibernate_sequence")
    @SequenceGenerator(name = "hibernate_sequence")
    private Long id;

    @Column(nullable = false)
    private String key;

    private Long counter;

    @ManyToOne(cascade = {CascadeType.DETACH})
    @JoinColumn(name = "num_template_id")
    private NumTemplateEntity template;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Long getCounter() {
        return counter;
    }

    public void setCounter(Long counter) {
        this.counter = counter;
    }

    public NumTemplateEntity getTemplate() {
        return template;
    }

    public void setTemplate(NumTemplateEntity template) {
        this.template = template;
    }
}
