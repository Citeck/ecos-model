package ru.citeck.ecos.model.service;

import ru.citeck.ecos.model.dto.EcosSectionDto;

import java.util.List;
import java.util.Optional;

public interface EcosSectionService {

    List<EcosSectionDto> getAll();

    List<EcosSectionDto> getAll(List<String> uuids);

    Optional<EcosSectionDto> getByUuid(String uuid);

    void delete(String uuid);

    EcosSectionDto update(EcosSectionDto dto);
}
