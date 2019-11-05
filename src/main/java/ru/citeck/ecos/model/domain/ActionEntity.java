package ru.citeck.ecos.model.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.persistence.*;

@Data
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Table(name = "ecos_actions")
public class ActionEntity extends BaseEntity {

    @Column(name = "ext_id")
    private String extId;

    private String name;
    private String type;
    private String key;
    private String icon;

    @Column(name = "action_order")
    private float order;

    @Lob
    @Column(name = "config_json")
    private String configJson;

    @ManyToOne
    @JoinColumn(name = "ecos_type_id")
    private TypeEntity ecosType;

    @OneToOne(cascade = {CascadeType.ALL}, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "ecos_evaluator_id")
    private EvaluatorEntity evaluator;

    @Override
    public String toString() {
        return "ActionEntity{" +
            "extId='" + extId + '\'' +
            ", name='" + name + '\'' +
            ", type='" + type + '\'' +
            ", key='" + key + '\'' +
            ", icon='" + icon + '\'' +
            ", order=" + order +
            ", configJson='" + configJson + '\'' +
            ", ecosType=" + ecosType.getId() +
            ", evaluator=" + evaluator.getId() +
            ", id=" + id +
            '}';
    }
}
