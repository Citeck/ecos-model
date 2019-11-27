package ru.citeck.ecos.model.service;

import ru.citeck.ecos.model.dto.SectionDto;

import java.util.Set;

public interface SectionService {

    Set<SectionDto> getAll();

    Set<SectionDto> getAll(Set<String> extIds);

    SectionDto getByExtId(String extId);

    void delete(String extId);

    SectionDto update(SectionDto dto);
}
