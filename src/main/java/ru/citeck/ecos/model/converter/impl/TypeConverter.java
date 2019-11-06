package ru.citeck.ecos.model.converter.impl;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.module.type.type.action.ActionDto;
import ru.citeck.ecos.model.converter.AbstractDtoConverter;
import ru.citeck.ecos.model.dao.TypeRecordsDao;
import ru.citeck.ecos.model.domain.AssociationEntity;
import ru.citeck.ecos.model.domain.TypeEntity;
import ru.citeck.ecos.model.dto.AssociationDto;
import ru.citeck.ecos.model.dto.TypeDto;
import ru.citeck.ecos.model.repository.TypeRepository;
import ru.citeck.ecos.model.service.exception.ParentNotFoundException;
import ru.citeck.ecos.model.service.exception.TypeNotFoundException;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class TypeConverter extends AbstractDtoConverter<TypeDto, TypeEntity> {

    private final TypeRepository typeRepository;
    private final ActionConverter actionConverter;

    @Autowired
    public TypeConverter(TypeRepository typeRepository, ActionConverter actionConverter) {
        this.typeRepository = typeRepository;
        this.actionConverter = actionConverter;
    }

    @Override
    protected TypeEntity dtoToEntity(TypeDto dto) {
        TypeEntity typeEntity = new TypeEntity();
        typeEntity.setName(dto.getName());
        String extId = extractId(dto.getId());
        typeEntity.setExtId(extId);
        typeEntity.setDescription(dto.getDescription());
        typeEntity.setTenant(dto.getTenant());
        typeEntity.setInheritActions(dto.isInheritActions());

        TypeEntity parent = null;
        if (dto.getParent() != null && Strings.isNotBlank(dto.getParent().getId())) {
            String parentId = extractId(dto.getParent().getId());
            parent = typeRepository.findByExtId(parentId).orElseThrow(() -> new ParentNotFoundException(parentId));
        }
        typeEntity.setParent(parent);

        Set<AssociationDto> associationDtos = dto.getAssociations();
        Set<AssociationEntity> associationEntities = null;
        if (associationDtos != null && associationDtos.size() != 0) {
            associationEntities = convertAssociations(associationDtos, typeEntity);
        }
        typeEntity.setAssocsToOther(associationEntities);
        handlingExtId(typeEntity);

        typeEntity.addActions(
            dto.getActions()
                .stream()
                .map(actionConverter::dtoToEntity)
                .collect(Collectors.toList())
        );

        return typeEntity;
    }

    private Set<AssociationEntity> convertAssociations(Set<AssociationDto> associationDtos,
                                                       TypeEntity sourceType) {
        return associationDtos.stream()
            .map(assocDto -> {
                AssociationEntity entity = new AssociationEntity();
                entity.setSource(sourceType);
                entity.setName(assocDto.getName());
                entity.setExtId(assocDto.getId());
                entity.setTitle(assocDto.getTitle());

                RecordRef targetRef = assocDto.getTargetType();
                TypeEntity targetType = null;
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

    private void handlingExtId(TypeEntity typeEntity) {
        if (Strings.isBlank(typeEntity.getExtId())) {
            typeEntity.setExtId(UUID.randomUUID().toString());
        } else {
            Optional<TypeEntity> stored = typeRepository.findByExtId(typeEntity.getExtId());
            typeEntity.setId(stored.map(TypeEntity::getId).orElse(null));
        }
        if (typeEntity.getAssocsToOther() != null) {
            typeEntity.setAssocsToOther(typeEntity.getAssocsToOther().stream().peek(e -> {
                if (e.getExtId() == null || StringUtils.isEmpty(e.getExtId())) {
                    e.setExtId(UUID.randomUUID().toString());
                }
            }).collect(Collectors.toSet()));
        }
    }

    @Override
    protected TypeDto entityToDto(TypeEntity entity) {
        TypeDto dto = new TypeDto();
        dto.setId(entity.getExtId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setInheritActions(entity.isInheritActions());
        dto.setTenant(entity.getTenant());

        TypeEntity parent = entity.getParent();
        if (parent != null) {
            dto.setParent(RecordRef.create(TypeRecordsDao.ID, parent.getExtId()));
        }

        Set<AssociationEntity> associations = entity.getAssocsToOther();
        if (associations != null && !associations.isEmpty()) {
            dto.setAssociations(associations.stream()
                .map(assocEntity -> {
                    AssociationDto assocDto = new AssociationDto();
                    assocDto.setId(assocEntity.getExtId());
                    assocDto.setName(assocEntity.getName());
                    assocDto.setTitle(assocEntity.getTitle());
                    assocDto.setTargetType(RecordRef.create(TypeRecordsDao.ID, assocEntity.getTarget().getExtId()));
                    assocDto.setSourceType(RecordRef.create(TypeRecordsDao.ID, assocEntity.getSource().getExtId()));
                    return assocDto;
                })
                .collect(Collectors.toSet()));
        }

        Set<ActionDto> actions = entity.getActions()
            .stream()
            .map(actionConverter::entityToDto)
            .collect(Collectors.toSet());

        dto.setActions(actions);
        return dto;
    }
}
