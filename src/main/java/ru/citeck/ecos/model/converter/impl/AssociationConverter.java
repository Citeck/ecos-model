package ru.citeck.ecos.model.converter.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.converter.AbstractDtoConverter;
import ru.citeck.ecos.model.converter.DtoConverter;
import ru.citeck.ecos.model.domain.AssociationEntity;
import ru.citeck.ecos.model.domain.TypeEntity;
import ru.citeck.ecos.model.dto.AssociationDto;
import ru.citeck.ecos.model.dto.TypeDto;
import ru.citeck.ecos.model.service.TypeService;
import ru.citeck.ecos.records2.RecordRef;

@Component
public class AssociationConverter extends AbstractDtoConverter<AssociationDto, AssociationEntity> {

    private final TypeService typeService;
    private final DtoConverter<TypeDto, TypeEntity> typeConverter;

    @Autowired
    public AssociationConverter(TypeService typeService,
                                DtoConverter<TypeDto, TypeEntity> typeConverter) {
        this.typeService = typeService;
        this.typeConverter = typeConverter;
    }

    @Override
    public AssociationEntity dtoToEntity(AssociationDto associationDto) {
        AssociationEntity associationEntity = new AssociationEntity();

        TypeDto sourceTypeDto = typeService.getByExtId(associationDto.getSourceType().getId());
        TypeEntity sourceTypeEntity = typeConverter.dtoToEntity(sourceTypeDto);
        associationEntity.setSource(sourceTypeEntity);

        TypeDto targetTypeDto = typeService.getByExtId(associationDto.getTargetType().getId());
        TypeEntity targetTypeEntity = typeConverter.dtoToEntity(targetTypeDto);
        associationEntity.setTarget(targetTypeEntity);

        associationEntity.setName(associationDto.getName());
        associationEntity.setExtId(associationDto.getId());
        associationEntity.setTitle(associationDto.getTitle());

        return associationEntity;
    }

    @Override
    public AssociationDto entityToDto(AssociationEntity associationEntity) {
        AssociationDto assocDto = new AssociationDto();
        assocDto.setId(associationEntity.getExtId());
        assocDto.setName(associationEntity.getName());
        assocDto.setTitle(associationEntity.getTitle());
        assocDto.setTargetType(RecordRef.create("type", associationEntity.getTarget().getExtId()));
        assocDto.setSourceType(RecordRef.create("type", associationEntity.getSource().getExtId()));
        return assocDto;
    }
}
