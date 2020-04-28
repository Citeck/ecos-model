package ru.citeck.ecos.model.association.service;

import ru.citeck.ecos.model.association.domain.AssociationEntity;
import ru.citeck.ecos.model.type.dto.TypeDto;

import java.util.Set;

public interface AssociationService {

    void extractAndSaveAssocsFromType(TypeDto dto);

    void saveAll(Set<AssociationEntity> associationEntities);

}