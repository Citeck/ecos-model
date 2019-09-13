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
import ru.citeck.ecos.model.service.exception.ForgottenChildsException;
import ru.citeck.ecos.model.service.exception.ParentNotFoundException;
import ru.citeck.ecos.records2.RecordRef;
import springfox.documentation.annotations.Cacheable;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
    public Set<EcosTypeDto> getAll() {
        return typeRepository.findAll().stream()
            .map(this::entityToDto)
            .collect(Collectors.toSet());
    }

    @Override
    public Set<EcosTypeDto> getAll(Set<String> extIds) {
        return typeRepository.findAllByExtIds(extIds).stream()
            .map(this::entityToDto)
            .collect(Collectors.toSet());
    }

    @Override
    public EcosTypeDto getByExtId(String extId) {
        return typeRepository.findByExtId(extId).map(this::entityToDto)
            .orElseThrow(() -> new IllegalArgumentException("Type doesnt exists: " + extId));
    }

    @Override
    @Transactional
    public void delete(String extId) {
        Optional<EcosTypeEntity> optional = typeRepository.findByExtId(extId);
        optional.ifPresent(e -> {
            if (e.getChilds() != null) {
                throw new ForgottenChildsException();
            }
            typeRepository.deleteById(e.getId());
        });
    }

    @Override
    @Transactional
    public EcosTypeDto update(EcosTypeDto dto) {
        EcosTypeEntity entity = dtoToEntity(dto);
        if (Strings.isBlank(entity.getExtId())) {
            entity.setExtId(UUID.randomUUID().toString());
        } else {
            Optional<EcosTypeEntity> stored = typeRepository.findByExtId(entity.getExtId());
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

        EcosTypeEntity parent = null;
        if (dto.getParent() != null && Strings.isNotBlank(dto.getParent().getId())) {
            parent = typeRepository.findByExtId(dto.getParent().getId())
                .orElseThrow(() -> new ParentNotFoundException(dto.getParent().getId()));
        }
        ecosTypeEntity.setParent(parent);

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
