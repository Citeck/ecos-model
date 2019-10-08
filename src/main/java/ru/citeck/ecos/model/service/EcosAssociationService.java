package ru.citeck.ecos.model.service;

import ru.citeck.ecos.model.dto.EcosAssociationDto;

import java.util.Set;

public interface EcosAssociationService {

    Set<EcosAssociationDto> getAll();

    Set<EcosAssociationDto> getAll(Set<String> ids);

    EcosAssociationDto getByExtId(String id);

    void delete(String id);

    EcosAssociationDto update(EcosAssociationDto dto);
}
