package ru.citeck.ecos.model.type.service;

import org.springframework.data.domain.Sort;
import ru.citeck.ecos.model.type.dto.CreateVariantDto;
import ru.citeck.ecos.model.type.dto.TypeDto;
import ru.citeck.ecos.model.type.dto.TypeWithMetaDto;
import ru.citeck.ecos.records2.predicate.model.Predicate;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public interface TypeService {

    void addListener(Consumer<TypeDto> onTypeChangedListener);

    List<TypeWithMetaDto> getAll(int max, int skip);

    List<TypeWithMetaDto> getAll(int max, int skip, Predicate predicate);

    Set<TypeWithMetaDto> getAll();

    Set<TypeWithMetaDto> getAll(Collection<String> extIds);

    List<TypeWithMetaDto> getAll(int max, int skip, Predicate predicate, Sort sort);

    List<TypeWithMetaDto> getTypesByJournalList(String journalListId);

    TypeWithMetaDto getByExtId(String extId);

    TypeWithMetaDto getByExtIdOrNull(String extId);

    TypeWithMetaDto getOrCreateByExtId(String extId);

    List<TypeWithMetaDto> getParents(String extId);

    List<TypeWithMetaDto> getChildren(String extId);

    String getDashboardType(String extId);

    List<CreateVariantDto> getCreateVariants(String extId);

    void delete(String extId);

    TypeWithMetaDto save(TypeDto dto);

    int getCount(Predicate predicate);

    int getCount();
}
