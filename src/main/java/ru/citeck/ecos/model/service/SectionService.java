package ru.citeck.ecos.model.service;

import ru.citeck.ecos.model.dto.SectionDto;

import java.util.List;
import java.util.Set;

public interface SectionService {

    Set<SectionDto> getAll();

    List<SectionDto> getAll(int max, int skip);

    Set<SectionDto> getAll(Set<String> extIds);

    SectionDto getByExtId(String extId);

    void delete(String extId);

    SectionDto save(SectionDto dto);

    int getCount();
}
