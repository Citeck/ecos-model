package ru.citeck.ecos.model.service.impl;

import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.model.domain.EcosAssociationEntity;
import ru.citeck.ecos.model.domain.EcosTypeEntity;
import ru.citeck.ecos.model.dto.EcosAssociationDto;
import ru.citeck.ecos.model.repository.EcosAssociationRepository;
import ru.citeck.ecos.model.repository.EcosTypeRepository;
import ru.citeck.ecos.model.service.EcosAssociationService;
import ru.citeck.ecos.model.service.exception.AssociationCollisionException;
import ru.citeck.ecos.model.service.exception.TypeNotFoundException;
import ru.citeck.ecos.records2.RecordRef;
import springfox.documentation.annotations.Cacheable;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EcosAssociationServiceImpl implements EcosAssociationService {

    private EcosAssociationRepository associationRepository;
    private EcosTypeRepository typeRepository;

    @Autowired
    public EcosAssociationServiceImpl(EcosAssociationRepository associationRepository,
                                      EcosTypeRepository typeRepository) {
        this.associationRepository = associationRepository;
        this.typeRepository = typeRepository;
    }

    @Cacheable("associations")
    @Override
    public Set<EcosAssociationDto> getAll() {
        return associationRepository.findAll().stream()
            .map(this::entityToDto)
            .collect(Collectors.toSet());
    }

    @Override
    public Set<EcosAssociationDto> getAll(Set<String> extIds) {
        return associationRepository.findAllByExtIds(extIds).stream()
            .map(this::entityToDto)
            .collect(Collectors.toSet());
    }

    @Override
    public EcosAssociationDto getByExtId(String extId) {
        return associationRepository.findByExtId(extId).map(this::entityToDto)
            .orElseThrow(() -> new IllegalArgumentException("Association doesnt exists: " + extId));
    }

    @Override
    @Transactional
    public void delete(String extId) {
        Optional<EcosAssociationEntity> optional = associationRepository.findByExtId(extId);
        optional.ifPresent(e -> associationRepository.deleteById(e.getId()));
    }

    @Override
    @Transactional
    public EcosAssociationDto update(EcosAssociationDto dto) {
        EcosAssociationEntity entity = dtoToEntity(dto);
        boolean isStored = false;
        boolean isNameChanged = false;
        if (Strings.isBlank(entity.getExtId())) {
            entity.setExtId(UUID.randomUUID().toString());
        } else {
            Optional<EcosAssociationEntity> stored = associationRepository.findByExtId(entity.getExtId());
            entity.setId(stored.map(EcosAssociationEntity::getId).orElse(null));
            if (stored.isPresent() && !stored.get().getName().equals(dto.getName())) {
                isNameChanged = true;
            }
            isStored = true;
        }

        if (!isStored || isNameChanged) {
            checkName(entity);
        }

        associationRepository.save(entity);
        return entityToDto(entity);
    }

    private void checkName(EcosAssociationEntity assoc) {
        EcosTypeEntity type = assoc.getType();
        String checkedName = assoc.getName();
        boolean nameExists = type.getAssociations().stream()
            .map(EcosAssociationEntity::getName)
            .anyMatch(e -> e.equals(checkedName));
        if (nameExists) {
            throw new AssociationCollisionException(checkedName);
        }
    }

    private EcosAssociationDto entityToDto(EcosAssociationEntity entity) {
        RecordRef type = null;
        if (entity.getType() != null) {
            type = RecordRef.create("type", entity.getType().getExtId());
        }
        return new EcosAssociationDto(
            entity.getExtId(),
            entity.getName(),
            entity.getTitle(),
            type);
    }

    private EcosAssociationEntity dtoToEntity(EcosAssociationDto dto) {
        EcosAssociationEntity entity = new EcosAssociationEntity();
        entity.setName(dto.getName());
        entity.setExtId(dto.getExtId());
        entity.setTitle(dto.getTitle());

        RecordRef type = dto.getType();
        EcosTypeEntity typeEntity = null;
        if (type != null && Strings.isNotBlank(dto.getType().getId())) {
            typeEntity = typeRepository.findByExtId(dto.getType().getId())
                .orElseThrow(() -> new TypeNotFoundException(dto.getType().getId()));
        }
        entity.setType(typeEntity);

        return entity;
    }
}
