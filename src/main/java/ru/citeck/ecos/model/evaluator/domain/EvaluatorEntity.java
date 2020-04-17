package ru.citeck.ecos.model.evaluator.domain;

import lombok.*;
import ru.citeck.ecos.model.domain.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.Table;

@Data
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Table(name = "ecos_evaluators")
public class EvaluatorEntity extends BaseEntity {

    @Column(name = "ext_id")
    private String extId;

    @Lob
    @Column(name = "config_json")
    private String configJson;

}
