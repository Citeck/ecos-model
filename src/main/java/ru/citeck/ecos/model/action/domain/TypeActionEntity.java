package ru.citeck.ecos.model.action.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import ru.citeck.ecos.model.domain.BaseEntity;
import ru.citeck.ecos.model.type.domain.TypeEntity;

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
