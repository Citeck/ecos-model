package ru.citeck.ecos.model.converter.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.converter.AbstractDtoConverter;
import ru.citeck.ecos.model.domain.AssociationEntity;
import ru.citeck.ecos.model.domain.TypeEntity;
import ru.citeck.ecos.model.dto.AssociationDto;
import ru.citeck.ecos.model.repository.TypeRepository;

@Component
public class AssociationConverter extends AbstractDtoConverter<AssociationDto, AssociationEntity> {

    private final TypeRepository typeRepository;

    @Autowired
    public AssociationConverter(TypeRepository typeRepository) {
        this.typeRepository = typeRepository;
    }

    @Override
    public AssociationEntity dtoToEntity(AssociationDto associationDto) {
        AssociationEntity associationEntity = new AssociationEntity();

       /*
        TypeEntity targetTypeEntity = findType(associationDto.getTargetType().getId());
        associationEntity.setTarget(targetTypeEntity);
        */

        associationEntity.setName(associationDto.getName());
        associationEntity.setExtId(associationDto.getId());

        return associationEntity;
    }

    @Override
    public AssociationDto entityToDto(AssociationEntity associationEntity) {
        AssociationDto assocDto = new AssociationDto();
        assocDto.setId(associationEntity.getExtId());
        assocDto.setName(associationEntity.getName());
        //assocDto.setTargetType(RecordRef.create("type", associationEntity.getTarget().getExtId()));
        return assocDto;
    }

    private TypeEntity findType(String extId) {
        return typeRepository.findByExtId(extId)
            .orElseThrow(() -> new IllegalArgumentException("Type doesnt exists: " + extId));
    }
}
