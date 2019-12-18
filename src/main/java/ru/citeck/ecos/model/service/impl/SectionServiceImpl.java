package ru.citeck.ecos.model.service.impl;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.model.converter.Converter;
import ru.citeck.ecos.model.domain.SectionEntity;
import ru.citeck.ecos.model.dto.SectionDto;
import ru.citeck.ecos.model.repository.SectionRepository;
import ru.citeck.ecos.model.service.SectionService;
import springfox.documentation.annotations.Cacheable;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class SectionServiceImpl implements SectionService {

    private final SectionRepository sectionRepository;
    private final Converter<SectionDto, SectionEntity> sectionConverter;

    @Cacheable("sections")
    public Set<SectionDto> getAll() {
        return sectionRepository.findAll()
            .stream()
            .map(sectionConverter::targetToSource)
            .collect(Collectors.toSet());
    }

    @Override
    public Set<SectionDto> getAll(Set<String> extIds) {
        return sectionRepository.findAllByExtIds(extIds)
            .stream()
            .map(sectionConverter::targetToSource)
            .collect(Collectors.toSet());
    }

    @Override
    public SectionDto getByExtId(String extId) {
        return sectionRepository.findByExtId(extId).map(sectionConverter::targetToSource)
            .orElseThrow(() -> new IllegalArgumentException("Section doesnt exists: " + extId));
    }

    @Override
    @Transactional
    public void delete(String extId) {
        Optional<SectionEntity> optional = sectionRepository.findByExtId(extId);
        optional.ifPresent(e -> sectionRepository.deleteById(e.getId()));
    }

    @Override
    @Transactional
    public SectionDto update(SectionDto dto) {
        SectionEntity entity = sectionConverter.sourceToTarget(dto);
        sectionRepository.save(entity);
        return sectionConverter.targetToSource(entity);
    }

}
