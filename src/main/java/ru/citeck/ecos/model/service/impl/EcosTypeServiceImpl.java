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

import java.util.List;
import java.util.Optional;
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
    public List<EcosTypeDto> getAll() {
        return typeRepository.findAll().stream()
            .map(this::entityToDto)
            .collect(Collectors.toList());
    }

    @Override
    public List<EcosTypeDto> getAll(List<String> uuids) {
        return typeRepository.findAllByUuid(uuids).stream()
            .map(this::entityToDto)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<EcosTypeDto> getByUuid(String uuid) {
        return typeRepository.findByUuid(uuid).map(this::entityToDto);
    }

    @Override
    @Transactional
    public void delete(String uuid) {
        Optional<EcosTypeEntity> optional = typeRepository.findByUuid(uuid);
        optional.ifPresent(e -> typeRepository.deleteById(e.getId()));
    }

    @Override
    @Transactional
    public EcosTypeDto update(EcosTypeDto dto) {
        EcosTypeEntity entity = dtoToEntity(dto);
        if (dto.getParent() != null && Strings.isNotBlank(dto.getParent().getId())) {
            EcosTypeEntity parent = typeRepository.findByUuid(dto.getParent().getId())
                .orElseThrow(() -> new ParentNotFoundException(dto.getParent().getId()));
            entity.setParent(parent);
        }
        if (Strings.isBlank(entity.getUuid())) {
            entity.setUuid(UUID.randomUUID().toString());
        } else {
            Optional<EcosTypeEntity> stored = typeRepository.findByUuid(entity.getUuid());
            entity.setId(stored.map(EcosTypeEntity::getId).orElse(null));
        }
        typeRepository.save(entity);
        return entityToDto(entity);
    }

    private EcosTypeDto entityToDto(EcosTypeEntity entity) {
        RecordRef parent = null;
        if (entity.getParent() != null) {
            parent = RecordRef.create("type", entity.getParent().getUuid());
        }
        RecordRef section = null;
        if (entity.getSection() != null) {
            section = RecordRef.create("section", entity.getSection().getUuid());
        }
        return new EcosTypeDto(
            entity.getUuid(),
            entity.getName(),
            entity.getDescription(),
            entity.getTenant(),
            parent,
            section);
    }

    private EcosTypeEntity dtoToEntity(EcosTypeDto dto) {
        EcosTypeEntity ecosTypeEntity = new EcosTypeEntity();
        ecosTypeEntity.setName(dto.getName());
        ecosTypeEntity.setUuid(dto.getUuid());
        ecosTypeEntity.setDescription(dto.getDescription());
        ecosTypeEntity.setTenant(dto.getTenant());
        Optional<EcosTypeEntity> parent = typeRepository.findByUuid(dto.getParent().getId());
        ecosTypeEntity.setParent(parent.orElse(null));
        Optional<EcosSectionEntity> opSection = sectionRepository.findByUuid(dto.getSection().getId());
        ecosTypeEntity.setSection(opSection.orElse(null));
        return ecosTypeEntity;
    }

}
