package ru.citeck.ecos.model.service;

import ru.citeck.ecos.model.dto.EcosSectionDto;

import java.util.Set;

public interface EcosSectionService {

    Set<EcosSectionDto> getAll();

    Set<EcosSectionDto> getAll(Set<String> extIds);

    EcosSectionDto getByExtId(String extId);

    void delete(String extId);

    EcosSectionDto update(EcosSectionDto dto);
}
