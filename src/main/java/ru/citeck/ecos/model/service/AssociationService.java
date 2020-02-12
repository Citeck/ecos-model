package ru.citeck.ecos.model.service;

import ru.citeck.ecos.model.domain.AssociationEntity;
import ru.citeck.ecos.model.dto.TypeDto;

import java.util.Set;

public interface AssociationService {

    void extractAndSaveAssocsFromType(TypeDto dto);

    void saveAll(Set<AssociationEntity> associationEntities);

}
