package ru.citeck.ecos.model.type.service;

import ru.citeck.ecos.model.association.dto.AssociationDto;
import ru.citeck.ecos.model.type.dto.TypeDto;
import ru.citeck.ecos.records2.predicate.model.Predicate;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public interface TypeService {

    void addListener(Consumer<TypeDto> onTypeChangedListener);

    List<TypeDto> getAll(int max, int skip);

    List<TypeDto> getAll(int max, int skip, Predicate predicate);

    Set<TypeDto> getAll();

    Set<TypeDto> getAll(Collection<String> extIds);

    TypeDto getByExtId(String extId);

    TypeDto getByExtIdOrNull(String extId);

    TypeDto getOrCreateByExtId(String extId);

    List<TypeDto> getParents(String extId);

    List<TypeDto> getChildren(String extId);

    List<AssociationDto> getFullAssocs(String extId);

    String getDashboardType(String extId);

    void delete(String extId);

    TypeDto save(TypeDto dto);

    int getCount(Predicate predicate);

    int getCount();
}
