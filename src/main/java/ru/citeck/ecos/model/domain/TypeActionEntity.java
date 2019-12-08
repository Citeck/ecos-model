package ru.citeck.ecos.model.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.persistence.*;

@Data
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Table(name = "ecos_type_actions")
public class TypeActionEntity extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "type_id")
    private TypeEntity type;

    @Column(name = "action_ext_id")
    private String actionId;
}
