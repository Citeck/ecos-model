package ru.citeck.ecos.model.service.impl;

import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import ru.citeck.ecos.model.domain.EcosAssociationEntity;
import ru.citeck.ecos.model.dto.EcosAssociationDto;
import ru.citeck.ecos.model.repository.EcosAssociationRepository;
import ru.citeck.ecos.model.repository.EcosTypeRepository;
import ru.citeck.ecos.model.service.EcosAssociationService;
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
    public Set<EcosAssociationDto> getAll(Set<String> ids) {

        return associationRepository.findAllByExtIds(ids).stream()
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
        if (Strings.isBlank(entity.getExtId())) {
            entity.setExtId(UUID.randomUUID().toString());
        } else {
            Optional<EcosAssociationEntity> storedOptional = associationRepository.findByExtId(entity.getExtId());
            if (storedOptional.isPresent()) {
                EcosAssociationEntity stored = storedOptional.get();
                entity.setId(stored.getId());
            }
        }
        associationRepository.save(entity);
        return entityToDto(entity);
    }

    private EcosAssociationDto entityToDto(EcosAssociationEntity entity) {
        return new EcosAssociationDto(
            entity.getExtId(),
            entity.getName(),
            entity.getTitle(),
            RecordRef.create("type", entity.getSource().getExtId()),
            RecordRef.create("type", entity.getTarget().getExtId()));
    }

    private EcosAssociationEntity dtoToEntity(EcosAssociationDto dto) {
        EcosAssociationEntity entity = new EcosAssociationEntity();
        entity.setName(dto.getName());
        entity.setExtId(dto.getId());
        entity.setTitle(dto.getTitle());

        RecordRef sourceRef = dto.getSourceType();
        RecordRef targetRef = dto.getTargetType();
        if (sourceRef == null || targetRef == null || StringUtils.isEmpty(sourceRef.getId()) ||
            StringUtils.isEmpty(targetRef)) {
            throw new TypeNotFoundException(null);
        } else {
            entity.setSource(typeRepository.findByExtId(sourceRef.getId())
                .orElseThrow(() -> new TypeNotFoundException(sourceRef.getId())));
            entity.setTarget(typeRepository.findByExtId(targetRef.getId())
                .orElseThrow(() -> new TypeNotFoundException(targetRef.getId())));
        }

        return entity;
    }
}
