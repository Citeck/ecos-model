package ru.citeck.ecos.model.service;

import ru.citeck.ecos.model.dto.TypeDto;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public interface TypeService {

    void addListener(Consumer<TypeDto> onTypeChangedListener);

    List<TypeDto> getAll(int max, int skip);

    Set<TypeDto> getAll();

    Set<TypeDto> getAll(Set<String> extIds);

    TypeDto getByExtId(String extId);

    TypeDto getOrCreateByExtId(String extId);

    List<TypeDto> getParents(String extId);

    List<TypeDto> getChildren(String extId);

    String getDashboardType(String extId);

    void delete(String extId);

    TypeDto save(TypeDto dto);

    int getCount();
}
