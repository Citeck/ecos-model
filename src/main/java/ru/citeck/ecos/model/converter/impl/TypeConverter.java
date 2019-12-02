package ru.citeck.ecos.model.converter.impl;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.module.type.type.action.ActionDto;
import ru.citeck.ecos.model.converter.AbstractDtoConverter;
import ru.citeck.ecos.model.converter.DtoConverter;
import ru.citeck.ecos.model.dao.TypeRecordsDao;
import ru.citeck.ecos.model.domain.ActionEntity;
import ru.citeck.ecos.model.domain.AssociationEntity;
import ru.citeck.ecos.model.domain.TypeEntity;
import ru.citeck.ecos.model.dto.AssociationDto;
import ru.citeck.ecos.model.dto.TypeDto;
import ru.citeck.ecos.model.repository.TypeRepository;
import ru.citeck.ecos.model.service.exception.ParentNotFoundException;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class TypeConverter extends AbstractDtoConverter<TypeDto, TypeEntity> {

    private final TypeRepository typeRepository;
    private final DtoConverter<ActionDto, ActionEntity> actionConverter;
    private final DtoConverter<AssociationDto, AssociationEntity> associationConverter;

    @Autowired
    public TypeConverter(TypeRepository typeRepository,
                         DtoConverter<ActionDto, ActionEntity> actionConverter,
                         DtoConverter<AssociationDto, AssociationEntity> associationConverter) {
        this.typeRepository = typeRepository;
        this.actionConverter = actionConverter;
        this.associationConverter = associationConverter;
    }

    @Override
    public TypeEntity dtoToEntity(TypeDto dto) {

        String extId = extractId(dto.getId());

        TypeEntity typeEntity = new TypeEntity();
        typeEntity.setExtId(extId);
        typeEntity.setName(dto.getName());
        typeEntity.setDescription(dto.getDescription());
        typeEntity.setTenant(dto.getTenant());
        typeEntity.setInheritActions(dto.isInheritActions());

        if (dto.getParent() != null && Strings.isNotBlank(dto.getParent().getId())) {
            String parentId = extractId(dto.getParent().getId());
            TypeEntity parent = typeRepository.findByExtId(parentId)
                .orElseThrow(() -> new ParentNotFoundException(parentId));
            typeEntity.setParent(parent);
        }

        if (dto.getAssociations() != null) {
            typeEntity.setAssocsToOther(dto.getAssociations()
                .stream()
                .filter(a -> a != null && StringUtils.isNotBlank(a.getId()))
                .peek(associationDto -> associationDto.setSourceType(RecordRef.create("type", typeEntity.getExtId())))
                .map(associationConverter::dtoToEntity)
                .collect(Collectors.toSet()));
        }

        if (dto.getActions() != null) {
            typeEntity.addActions(
                dto.getActions()
                    .stream()
                    .filter(a -> a != null && StringUtils.isNotBlank(a.getId()))
                    .map(actionConverter::dtoToEntity)
                    .collect(Collectors.toList())
            );
        }

        handlingExtId(typeEntity);

        return typeEntity;
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
    public TypeDto entityToDto(TypeEntity entity) {
        TypeDto dto = new TypeDto();
        dto.setId(entity.getExtId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setInheritActions(entity.isInheritActions());
        dto.setTenant(entity.getTenant());

        if (entity.getParent() != null) {
            dto.setParent(RecordRef.create(TypeRecordsDao.ID, entity.getParent().getExtId()));
        }

        if (entity.getAssocsToOther() != null) {
            dto.setAssociations(entity.getAssocsToOther().stream()
                .map(associationConverter::entityToDto)
                .collect(Collectors.toSet()));
        }

        if (entity.getActions() != null) {
            dto.setActions(entity.getActions()
                .stream()
                .map(actionConverter::entityToDto)
                .collect(Collectors.toSet()));
        }

        return dto;
    }
}
