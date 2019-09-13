package ru.citeck.ecos.model.service.impl;


import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.model.domain.EcosSectionEntity;
import ru.citeck.ecos.model.domain.EcosTypeEntity;
import ru.citeck.ecos.model.dto.EcosSectionDto;
import ru.citeck.ecos.model.repository.EcosSectionRepository;
import ru.citeck.ecos.model.repository.EcosTypeRepository;
import ru.citeck.ecos.model.service.EcosSectionService;
import ru.citeck.ecos.records2.RecordRef;
import springfox.documentation.annotations.Cacheable;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class EcosSectionServiceImpl implements EcosSectionService {

    private final EcosSectionRepository sectionRepository;
    private final EcosTypeRepository typeRepository;

    @Autowired
    public EcosSectionServiceImpl(EcosSectionRepository sectionRepository, EcosTypeRepository typeRepository) {
        this.sectionRepository = sectionRepository;
        this.typeRepository = typeRepository;
    }

    @Cacheable("sections")
    public Set<EcosSectionDto> getAll() {
        return sectionRepository.findAll().stream()
            .map(this::entityToDto)
            .collect(Collectors.toSet());
    }

    @Override
    public Set<EcosSectionDto> getAll(Set<String> extIds) {
        return sectionRepository.findAllByExtIds(extIds).stream()
            .map(this::entityToDto)
            .collect(Collectors.toSet());
    }

    @Override
    public EcosSectionDto getByExtId(String extId) {
        return sectionRepository.findByExtId(extId).map(this::entityToDto)
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
        EcosSectionEntity entity = dtoToEntity(dto);
        if (Strings.isBlank(entity.getExtId())) {
            entity.setExtId(UUID.randomUUID().toString());
        } else {
            Optional<EcosSectionEntity> stored = sectionRepository.findByExtId(entity.getExtId());
            entity.setId(stored.map(EcosSectionEntity::getId).orElse(null));
        }
        sectionRepository.save(entity);
        return entityToDto(entity);
    }

    private EcosSectionDto entityToDto(EcosSectionEntity entity) {
        Set<RecordRef> typesRefs = null;
        if (entity.getTypes() != null) {
            typesRefs = entity.getTypes().stream()
                .map(e -> RecordRef.create("type", e.getExtId()))
                .collect(Collectors.toSet());
        }
        return new EcosSectionDto(
            entity.getExtId(),
            entity.getName(),
            entity.getDescription(),
            entity.getTenant(),
            typesRefs);
    }

    private EcosSectionEntity dtoToEntity(EcosSectionDto dto) {
        EcosSectionEntity ecosSectionEntity = new EcosSectionEntity();
        ecosSectionEntity.setName(dto.getName());
        ecosSectionEntity.setExtId(dto.getExtId());
        ecosSectionEntity.setDescription(dto.getDescription());
        ecosSectionEntity.setTenant(dto.getTenant());
        if (dto.getTypes() != null) {
            Set<String> dtoTypesExtIds = dto.getTypes().stream()
                .map(RecordRef::getId).collect(Collectors.toSet());
            Set<EcosTypeEntity> storedTypes = typeRepository.findAllByExtIds(dtoTypesExtIds);
            ecosSectionEntity.setTypes(storedTypes);
        }
        return ecosSectionEntity;
    }

}
