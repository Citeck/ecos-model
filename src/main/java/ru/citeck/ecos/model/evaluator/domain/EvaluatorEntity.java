package ru.citeck.ecos.model.evaluator.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import ru.citeck.ecos.model.domain.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.Table;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Table(name = "ecos_evaluators")
public class EvaluatorEntity extends BaseEntity {

    @Column(name = "ext_id")
    private String extId;

    @Lob
    @Column(name = "config_json")
    private String configJson;

}
