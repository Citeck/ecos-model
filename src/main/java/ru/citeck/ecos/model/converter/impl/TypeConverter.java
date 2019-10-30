package ru.citeck.ecos.model.converter.impl;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.module.type.type.action.ActionDto;
import ru.citeck.ecos.model.converter.AbstractDtoConverter;
import ru.citeck.ecos.model.dao.EcosTypeRecordsDao;
import ru.citeck.ecos.model.domain.EcosAssociationEntity;
import ru.citeck.ecos.model.domain.EcosTypeEntity;
import ru.citeck.ecos.model.dto.EcosAssociationDto;
import ru.citeck.ecos.model.dto.EcosTypeDto;
import ru.citeck.ecos.model.repository.EcosTypeRepository;
import ru.citeck.ecos.model.service.converter.ActionConverter;
import ru.citeck.ecos.model.service.exception.ParentNotFoundException;
import ru.citeck.ecos.model.service.exception.TypeNotFoundException;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class TypeConverter extends AbstractDtoConverter<EcosTypeDto, EcosTypeEntity> {

    private EcosTypeRepository typeRepository;

    @Autowired
    public TypeConverter(EcosTypeRepository typeRepository) {
        this.typeRepository = typeRepository;
    }

    @Override
    protected EcosTypeEntity dtoToEntity(EcosTypeDto dto) {
        EcosTypeEntity ecosTypeEntity = new EcosTypeEntity();
        ecosTypeEntity.setName(dto.getName());
        String extId = extractId(dto.getId());
        ecosTypeEntity.setExtId(extId);
        ecosTypeEntity.setDescription(dto.getDescription());
        ecosTypeEntity.setTenant(dto.getTenant());
        ecosTypeEntity.setInheritActions(dto.isInheritActions());

        EcosTypeEntity parent = null;
        if (dto.getParent() != null && Strings.isNotBlank(dto.getParent().getId())) {
            String parentId = extractId(dto.getParent().getId());
            parent = typeRepository.findByExtId(parentId).orElseThrow(() -> new ParentNotFoundException(parentId));
        }
        ecosTypeEntity.setParent(parent);

        Set<EcosAssociationDto> associationDtos = dto.getAssociations();
        Set<EcosAssociationEntity> associationEntities = null;
        if (associationDtos != null && associationDtos.size() != 0) {
            associationEntities = convertAssociations(associationDtos, ecosTypeEntity);
        }
        ecosTypeEntity.setAssocsToOther(associationEntities);
        handlingExtId(ecosTypeEntity);

        ecosTypeEntity.addActions(
            dto.getActions()
                .stream()
                .map(ActionConverter::fromDto)
                .collect(Collectors.toSet())
        );

        return ecosTypeEntity;
    }

    private Set<EcosAssociationEntity> convertAssociations(Set<EcosAssociationDto> associationDtos,
                                                           EcosTypeEntity sourceType) {
        return associationDtos.stream()
            .map(assocDto -> {
                EcosAssociationEntity entity = new EcosAssociationEntity();
                entity.setSource(sourceType);
                entity.setName(assocDto.getName());
                entity.setExtId(assocDto.getId());
                entity.setTitle(assocDto.getTitle());

                RecordRef targetRef = assocDto.getTargetType();
                EcosTypeEntity targetType = null;
                if (targetRef != null && !StringUtils.isEmpty(targetRef.getId())) {
                    String targetId = extractId(targetRef.getId());
                    targetType = typeRepository.findByExtId(targetId)
                        .orElseThrow(() -> new TypeNotFoundException(targetId));
                }
                entity.setTarget(targetType);

                return entity;
            })
            .collect(Collectors.toSet());
    }

    private void handlingExtId(EcosTypeEntity ecosTypeEntity) {
        if (Strings.isBlank(ecosTypeEntity.getExtId())) {
            ecosTypeEntity.setExtId(UUID.randomUUID().toString());
        } else {
            Optional<EcosTypeEntity> stored = typeRepository.findByExtId(ecosTypeEntity.getExtId());
            ecosTypeEntity.setId(stored.map(EcosTypeEntity::getId).orElse(null));
        }
        if (ecosTypeEntity.getAssocsToOther() != null) {
            ecosTypeEntity.setAssocsToOther(ecosTypeEntity.getAssocsToOther().stream().peek(e -> {
                if (e.getExtId() == null || StringUtils.isEmpty(e.getExtId())) {
                    e.setExtId(UUID.randomUUID().toString());
                }
            }).collect(Collectors.toSet()));
        }
    }

    @Override
    protected EcosTypeDto entityToDto(EcosTypeEntity entity) {
        EcosTypeDto dto = new EcosTypeDto();
        dto.setId(entity.getExtId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setInheritActions(entity.isInheritActions());
        EcosTypeEntity parent = entity.getParent();
        if (parent != null) {
            dto.setParent(RecordRef.create(EcosTypeRecordsDao.ID, parent.getExtId()));
        }
        Set<EcosAssociationEntity> associations = entity.getAssocsToOther();
        if (associations != null && !associations.isEmpty()) {
            dto.setAssociations(associations.stream()
                .map(assocEntity -> {
                    EcosAssociationDto assocDto = new EcosAssociationDto();
                    assocDto.setId(assocEntity.getExtId());
                    assocDto.setName(assocEntity.getName());
                    assocDto.setTitle(assocEntity.getTitle());
                    assocDto.setTargetType(RecordRef.create(EcosTypeRecordsDao.ID, assocEntity.getTarget().getExtId()));
                    assocDto.setSourceType(RecordRef.create(EcosTypeRecordsDao.ID, assocEntity.getSource().getExtId()));
                    return assocDto;
                })
                .collect(Collectors.toSet()));
        }
        Set<ActionDto> actions = entity.getActions()
            .stream()
            .map(ActionConverter::toDto)
            .collect(Collectors.toSet());

        dto.setActions(actions);
        return dto;
    }
}
