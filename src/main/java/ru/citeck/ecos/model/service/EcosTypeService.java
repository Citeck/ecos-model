package ru.citeck.ecos.model.service;

import ru.citeck.ecos.model.dto.EcosTypeDto;

import java.util.Set;

public interface EcosTypeService {

    Set<EcosTypeDto> getAll();

    Set<EcosTypeDto> getAll(Set<String> extIds);

    EcosTypeDto getByExtId(String extId);

    void delete(String extId);

    EcosTypeDto update(EcosTypeDto dto);
}
