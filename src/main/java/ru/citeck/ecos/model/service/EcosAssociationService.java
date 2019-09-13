package ru.citeck.ecos.model.service;

import ru.citeck.ecos.model.dto.EcosAssociationDto;

import java.util.Set;

public interface EcosAssociationService {

    Set<EcosAssociationDto> getAll();

    Set<EcosAssociationDto> getAll(Set<String> extIds);

    EcosAssociationDto getByExtId(String extId);

    void delete(String extId);

    EcosAssociationDto update(EcosAssociationDto dto);
}
