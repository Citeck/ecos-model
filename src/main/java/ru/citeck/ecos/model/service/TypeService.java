package ru.citeck.ecos.model.service;

import ru.citeck.ecos.model.dto.TypeDto;

import java.util.List;
import java.util.Set;

public interface TypeService {

    Set<TypeDto> getAll();

    Set<TypeDto> getAll(Set<String> extIds);

    TypeDto getByExtId(String extId);

    List<TypeDto> getParents(String extId);

    String getDashboardType(String extId);

    void delete(String extId);

    TypeDto save(TypeDto dto);
}
