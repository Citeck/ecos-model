package ru.citeck.ecos.model.association.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class AssociationId implements Serializable {

    private String extId;
    private Long sourceId;
}
