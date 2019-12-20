package ru.citeck.ecos.model.converter.dto.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.module.ModuleRef;
import ru.citeck.ecos.model.converter.dto.AbstractDtoConverter;
import ru.citeck.ecos.model.converter.dto.DtoConverter;
import ru.citeck.ecos.model.dao.TypeRecordsDao;
import ru.citeck.ecos.model.domain.AssociationEntity;
import ru.citeck.ecos.model.domain.TypeActionEntity;
import ru.citeck.ecos.model.domain.TypeEntity;
import ru.citeck.ecos.model.dto.TypeAssociationDto;
import ru.citeck.ecos.model.dto.TypeDto;
import ru.citeck.ecos.model.repository.AssociationRepository;
import ru.citeck.ecos.model.repository.TypeRepository;
import ru.citeck.ecos.records2.RecordRef;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class TypeConverter extends AbstractDtoConverter<TypeDto, TypeEntity> {

    private final TypeRepository typeRepository;
    private final AssociationRepository associationRepository;
    private final DtoConverter<TypeAssociationDto, AssociationEntity> associationConverter;

    @Override
    public TypeEntity dtoToEntity(TypeDto dto) {

        TypeEntity typeEntity = new TypeEntity();

        String typeDtoId = dto.getId();
        if (Strings.isBlank(typeDtoId)) {
            typeEntity.setExtId(UUID.randomUUID().toString());
        } else {
            typeEntity.setExtId(typeDtoId);
        }

        typeEntity.setName(dto.getName());
        typeEntity.setDescription(dto.getDescription());
        typeEntity.setTenant(dto.getTenant());
        typeEntity.setInheritActions(dto.isInheritActions());
        typeEntity.setForm(dto.getForm());

        RecordRef parentRef = dto.getParent();
        if (parentRef != null && Strings.isNotBlank(parentRef.getId())) {
            Optional<TypeEntity> optionalParent = typeRepository.findByExtId(parentRef.getId());
            optionalParent.ifPresent(typeEntity::setParent);
        }

        Set<AssociationEntity> associationEntities = dto.getAssociations().stream()
            .filter(a -> StringUtils.isNotBlank(a.getId()))
            .map(a -> {
                AssociationEntity assocEntity = associationConverter.dtoToEntity(a);

                assocEntity.setSource(typeEntity);

                String targetTypeId = a.getTargetType().getId();
                TypeEntity targetTypeEntity = typeRepository.findByExtId(targetTypeId)
                    .orElseThrow(() -> new IllegalArgumentException("Type doesnt exists: " + targetTypeId));
                assocEntity.setTarget(targetTypeEntity);

                return assocEntity;
            })
            .collect(Collectors.toSet());
        typeEntity.setAssocsToOther(associationEntities);

        Optional<TypeEntity> storedType = typeRepository.findByExtId(typeEntity.getExtId());
        storedType.ifPresent(t -> {
            typeEntity.setId(t.getId());
        });

        List<TypeActionEntity> actionEntities = dto.getActions().stream()
            .filter(a -> StringUtils.isNotBlank(a.getId()))
            .map(a -> new TypeActionEntity(typeEntity, a.toString()))
            .collect(Collectors.toList());
        typeEntity.addActions(actionEntities);

        return typeEntity;
    }

    @Override
    public TypeDto entityToDto(TypeEntity entity) {

        TypeDto dto = new TypeDto();

        dto.setId(entity.getExtId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setInheritActions(entity.isInheritActions());
        dto.setTenant(entity.getTenant());
        dto.setForm(entity.getForm());

        TypeEntity parent = entity.getParent();
        if (parent != null) {
            RecordRef parentRecordRef = RecordRef.create(TypeRecordsDao.ID, parent.getExtId());
            dto.setParent(parentRecordRef);
        }

        Set<TypeAssociationDto> associationDtoSet = entity.getAssocsToOther().stream()
            .map(associationConverter::entityToDto)
            .collect(Collectors.toSet());
        dto.setAssociations(associationDtoSet);

        Set<ModuleRef> actionsModuleRefs = entity.getActions().stream()
            .map(a -> ModuleRef.valueOf(a.getActionId()))
            .collect(Collectors.toSet());
        dto.setActions(actionsModuleRefs);

        return dto;
    }
}
