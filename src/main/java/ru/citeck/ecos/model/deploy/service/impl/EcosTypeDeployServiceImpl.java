package ru.citeck.ecos.model.deploy.service.impl;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.model.deploy.dto.EcosTypeDeployDto;
import ru.citeck.ecos.model.deploy.service.EcosTypeDeployService;
import ru.citeck.ecos.model.domain.EcosAssociationEntity;
import ru.citeck.ecos.model.domain.EcosTypeEntity;
import ru.citeck.ecos.model.dto.EcosAssociationDto;
import ru.citeck.ecos.model.repository.EcosAssociationRepository;
import ru.citeck.ecos.model.repository.EcosTypeRepository;
import ru.citeck.ecos.model.service.exception.ParentNotFoundException;
import ru.citeck.ecos.model.service.exception.TypeNotFoundException;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/*
 * Service works like TypeDeployService, but needed because we have different ways to save types on backend.
 *
 * @see ru.citeck.ecos.model.service.impl.EcosTypeServiceImpl
 */
@Service
public class EcosTypeDeployServiceImpl implements EcosTypeDeployService {

    private EcosTypeRepository typeRepository;
    private EcosAssociationRepository associationRepository;

    @Autowired
    public EcosTypeDeployServiceImpl(EcosTypeRepository typeRepository, EcosAssociationRepository associationRepository) {
        this.typeRepository = typeRepository;
        this.associationRepository = associationRepository;
    }

    @Override
    @Transactional
    public void deploy(EcosTypeDeployDto dto) {
        EcosTypeEntity entity = dtoToEntity(dto);
        if (Strings.isBlank(entity.getExtId())) {
            entity.setExtId(UUID.randomUUID().toString());
        } else {
            Optional<EcosTypeEntity> stored = typeRepository.findByExtId(entity.getExtId());
            entity.setId(stored.map(EcosTypeEntity::getId).orElse(null));
        }
        if (entity.getAssocsToOther() != null) {
            entity.setAssocsToOther(entity.getAssocsToOther().stream().peek(e -> {
                if (e.getExtId() == null || StringUtils.isEmpty(e.getExtId())) {
                    e.setExtId(UUID.randomUUID().toString());
                }
            }).collect(Collectors.toSet()));
        }
        typeRepository.save(entity);
        associationRepository.saveAll(entity.getAssocsToOther().stream().
            peek(assoc -> {
                if (Strings.isBlank(assoc.getExtId())) {
                    assoc.setExtId(UUID.randomUUID().toString());
                }
            }).collect(Collectors.toList()));
    }

    private EcosTypeEntity dtoToEntity(EcosTypeDeployDto dto) {
        EcosTypeEntity ecosTypeEntity = new EcosTypeEntity();
        ecosTypeEntity.setName(dto.getName());
        ecosTypeEntity.setExtId(dto.getId());
        ecosTypeEntity.setDescription(dto.getDescription());
        ecosTypeEntity.setTenant(dto.getTenant());

        EcosTypeEntity parent = null;
        if (dto.getParent() != null && Strings.isNotBlank(dto.getParent().getId())) {
            parent = typeRepository.findByExtId(dto.getParent().getId())
                .orElseThrow(() -> new ParentNotFoundException(dto.getParent().getId()));
        }
        ecosTypeEntity.setParent(parent);

        Set<EcosAssociationDto> associationDtos = dto.getAssociations();
        Set<EcosAssociationEntity> associationEntities = null;
        if (associationDtos != null && associationDtos.size() != 0) {
            associationEntities = associationDtos.stream()
                .map(assocDto -> {
                    EcosAssociationEntity entity = new EcosAssociationEntity();
                    entity.setSource(ecosTypeEntity);
                    entity.setName(assocDto.getName());
                    entity.setExtId(assocDto.getId());
                    entity.setTitle(assocDto.getTitle());

                    RecordRef targetRef = assocDto.getTargetType();
                    EcosTypeEntity targetType = null;
                    if (targetRef != null && !StringUtils.isEmpty(targetRef.getId())) {
                        targetType = typeRepository.findByExtId(targetRef.getId())
                            .orElseThrow(() -> new TypeNotFoundException(targetRef.getId()));
                    }
                    entity.setTarget(targetType);

                    return entity;
                })
                .collect(Collectors.toSet());
        }
        ecosTypeEntity.setAssocsToOther(associationEntities);

        return ecosTypeEntity;
    }
}
