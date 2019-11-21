package ru.citeck.ecos.model.service;

import ru.citeck.ecos.model.domain.AssociationEntity;

import java.util.Set;

public interface AssociationService {

    void saveAll(Set<AssociationEntity> associationEntities);

}
