package ru.citeck.ecos.model.service.impl;


import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.model.domain.EcosSectionEntity;
import ru.citeck.ecos.model.domain.EcosTypeEntity;
import ru.citeck.ecos.model.dto.EcosTypeDto;
import ru.citeck.ecos.model.repository.EcosSectionRepository;
import ru.citeck.ecos.model.repository.EcosTypeRepository;
import ru.citeck.ecos.model.service.EcosTypeService;
import ru.citeck.ecos.model.service.exception.ParentNotFoundException;
import ru.citeck.ecos.records2.RecordRef;
import springfox.documentation.annotations.Cacheable;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class EcosTypeServiceImpl implements EcosTypeService {

    private final EcosTypeRepository typeRepository;
    private final EcosSectionRepository sectionRepository;

    @Autowired
    public EcosTypeServiceImpl(EcosTypeRepository typeRepository,
                               EcosSectionRepository sectionRepository) {
        this.typeRepository = typeRepository;
        this.sectionRepository = sectionRepository;
    }

    @Cacheable("types")
    public List<EcosTypeDto> getAll() {
        return typeRepository.findAll().stream()
            .map(this::entityToDto)
            .collect(Collectors.toList());
    }

    @Override
    public List<EcosTypeDto> getAll(List<String> uuids) {
        return typeRepository.findAllByExtIds(uuids).stream()
            .map(this::entityToDto)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<EcosTypeDto> getByUuid(String uuid) {
        return typeRepository.findByExtIds(uuid).map(this::entityToDto);
    }

    @Override
    @Transactional
    public void delete(String uuid) {
        Optional<EcosTypeEntity> optional = typeRepository.findByExtIds(uuid);
        optional.ifPresent(e -> typeRepository.deleteById(e.getId()));
    }

    @Override
    @Transactional
    public EcosTypeDto update(EcosTypeDto dto) {
        EcosTypeEntity entity = dtoToEntity(dto);
        if (dto.getParent() != null && Strings.isNotBlank(dto.getParent().getId())) {
            EcosTypeEntity parent = typeRepository.findByExtIds(dto.getParent().getId())
                .orElseThrow(() -> new ParentNotFoundException(dto.getParent().getId()));
            entity.setParent(parent);
        }
        if (Strings.isBlank(entity.getExtId())) {
            entity.setExtId(UUID.randomUUID().toString());
        } else {
            Optional<EcosTypeEntity> stored = typeRepository.findByExtIds(entity.getExtId());
            entity.setId(stored.map(EcosTypeEntity::getId).orElse(null));
        }
        typeRepository.save(entity);
        return entityToDto(entity);
    }

    private EcosTypeDto entityToDto(EcosTypeEntity entity) {
        RecordRef parent = null;
        if (entity.getParent() != null) {
            parent = RecordRef.create("type", entity.getParent().getExtId());
        }
        Set<RecordRef> sections = null;
        if (entity.getSections() != null) {
            sections = entity.getSections().stream()
                .map(s -> RecordRef.create("section", s.getExtId()))
                .collect(Collectors.toSet());
        }
        return new EcosTypeDto(
            entity.getExtId(),
            entity.getName(),
            entity.getDescription(),
            entity.getTenant(),
            parent,
            sections);
    }

    private EcosTypeEntity dtoToEntity(EcosTypeDto dto) {
        EcosTypeEntity ecosTypeEntity = new EcosTypeEntity();
        ecosTypeEntity.setName(dto.getName());
        ecosTypeEntity.setExtId(dto.getExtId());
        ecosTypeEntity.setDescription(dto.getDescription());
        ecosTypeEntity.setTenant(dto.getTenant());

        RecordRef parent = dto.getParent();
        Optional<EcosTypeEntity> optional = Optional.empty();
        if (parent != null && parent.getId() != null) {
            optional = typeRepository.findByExtIds(dto.getParent().getId());
        }
        ecosTypeEntity.setParent(optional.orElse(null));

        Set<RecordRef> sectionsExtIds = dto.getSections();
        Set<EcosSectionEntity> sections = null;
        if (sectionsExtIds != null) {
            sections = sectionRepository.findAllByExtIds(sectionsExtIds.stream()
                .filter(Objects::nonNull)
                .map(RecordRef::getId)
                .collect(Collectors.toSet()));
        }
        ecosTypeEntity.setSections(sections);
        return ecosTypeEntity;
    }

}
