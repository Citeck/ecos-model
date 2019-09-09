package ru.citeck.ecos.model.service;

import ru.citeck.ecos.model.dto.EcosTypeDto;

import java.util.List;
import java.util.Optional;

public interface EcosTypeService {

    List<EcosTypeDto> getAll();

    List<EcosTypeDto> getAll(List<String> uuids);

    Optional<EcosTypeDto> getByUuid(String uuid);

    void delete(String uuid);

    EcosTypeDto update(EcosTypeDto dto);
}
