package ru.citeck.ecos.model.service;

import ru.citeck.ecos.model.dto.TypeDto;

import java.util.Set;

public interface TypeService {

    Set<TypeDto> getAll();

    Set<TypeDto> getAll(Set<String> extIds);

    TypeDto getByExtId(String extId);

    void delete(String extId);

    TypeDto save(TypeDto dto);
}
