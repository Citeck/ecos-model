package ru.citeck.ecos.model.type.service;

import org.springframework.data.domain.Sort;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.model.type.dto.CreateVariantDto;
import ru.citeck.ecos.model.association.dto.AssociationDto;
import ru.citeck.ecos.model.type.dto.TypeDto;
import ru.citeck.ecos.model.type.dto.TypeWithMetaDto;
import ru.citeck.ecos.records2.RecordRef;
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

    TypeWithMetaDto getByExtId(String extId);

    TypeWithMetaDto getByExtIdOrNull(String extId);

    TypeWithMetaDto getOrCreateByExtId(String extId);

    List<TypeWithMetaDto> getParents(String extId);

    List<TypeWithMetaDto> getChildren(String extId);

    List<AssociationDto> getFullAssocs(String extId);

    String getDashboardType(String extId);

    List<CreateVariantDto> getCreateVariants(String extId);

    DataValue getInhAttribute(String extId, String name);

    RecordRef getConfigFormRef(String extId);

    void delete(String extId);

    TypeWithMetaDto save(TypeDto dto);

    int getCount(Predicate predicate);

    int getCount();
}
