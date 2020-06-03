package ru.citeck.ecos.model.association.service;

import ru.citeck.ecos.model.association.domain.AssociationEntity;
import ru.citeck.ecos.model.type.dto.TypeDto;
import ru.citeck.ecos.model.type.dto.TypeWithMetaDto;

import java.util.Set;

public interface AssociationService {

    void saveAll(Set<AssociationEntity> associationEntities);

}
