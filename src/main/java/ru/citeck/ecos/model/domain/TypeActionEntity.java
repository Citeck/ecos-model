package ru.citeck.ecos.model.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Entity
@Table(name = "ecos_type_actions")
public class TypeActionEntity extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "type_id")
    private TypeEntity type;

    @Column(name = "action_ext_id")
    private String actionId;
}
