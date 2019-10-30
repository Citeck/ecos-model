package ru.citeck.ecos.model.service.impl;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.model.converter.Converter;
import ru.citeck.ecos.model.domain.EcosSectionEntity;
import ru.citeck.ecos.model.dto.EcosSectionDto;
import ru.citeck.ecos.model.repository.EcosSectionRepository;
import ru.citeck.ecos.model.service.EcosSectionService;
import springfox.documentation.annotations.Cacheable;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EcosSectionServiceImpl implements EcosSectionService {

    private final EcosSectionRepository sectionRepository;
    private final Converter<EcosSectionDto, EcosSectionEntity> converter;

    @Autowired
    public EcosSectionServiceImpl(EcosSectionRepository sectionRepository,
                                  Converter<EcosSectionDto, EcosSectionEntity> converter) {
        this.sectionRepository = sectionRepository;
        this.converter = converter;
    }

    @Cacheable("sections")
    public Set<EcosSectionDto> getAll() {
        return sectionRepository.findAll()
            .stream()
            .map(converter::targetToSource)
            .collect(Collectors.toSet());
    }

    @Override
    public Set<EcosSectionDto> getAll(Set<String> extIds) {
        return sectionRepository.findAllByExtIds(extIds)
            .stream()
            .map(converter::targetToSource)
            .collect(Collectors.toSet());
    }

    @Override
    public EcosSectionDto getByExtId(String extId) {
        return sectionRepository.findByExtId(extId).map(converter::targetToSource)
            .orElseThrow(() -> new IllegalArgumentException("Section doesnt exists: " + extId));
    }

    @Override
    @Transactional
    public void delete(String extId) {
        Optional<EcosSectionEntity> optional = sectionRepository.findByExtId(extId);
        optional.ifPresent(e -> sectionRepository.deleteById(e.getId()));
    }

    @Override
    @Transactional
    public EcosSectionDto update(EcosSectionDto dto) {
        EcosSectionEntity entity = converter.sourceToTarget(dto);
        sectionRepository.save(entity);
        return converter.targetToSource(entity);
    }

}
