package ru.citeck.ecos.model.section.service;

import org.jetbrains.annotations.Nullable;
import ru.citeck.ecos.model.section.dto.SectionDto;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public interface SectionService {

    Set<SectionDto> getAll();

    void addListener(Consumer<SectionDto> listener);

    List<SectionDto> getAll(int max, int skip);

    Set<SectionDto> getAll(Set<String> extIds);

    @Nullable
    SectionDto getByExtId(String extId);

    void delete(String extId);

    SectionDto save(SectionDto dto);

    int getCount();
}
