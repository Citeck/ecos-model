package ru.citeck.ecos.model.association.service;

import ru.citeck.ecos.model.association.domain.AssociationEntity;

import java.util.Set;

public interface AssociationService {

    void saveAll(Set<AssociationEntity> associationEntities);
}
