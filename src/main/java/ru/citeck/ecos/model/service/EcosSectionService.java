package ru.citeck.ecos.model.service;

import ru.citeck.ecos.model.dto.EcosSectionDto;

import java.util.Optional;
import java.util.Set;

public interface EcosSectionService {

    Set<EcosSectionDto> getAll();

    Set<EcosSectionDto> getAll(Set<String> uuids);

    Optional<EcosSectionDto> getByUuid(String uuid);

    void delete(String uuid);

    EcosSectionDto update(EcosSectionDto dto);
}
