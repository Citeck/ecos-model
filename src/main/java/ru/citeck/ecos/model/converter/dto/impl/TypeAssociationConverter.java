package ru.citeck.ecos.model.converter.dto.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.converter.dto.AbstractDtoConverter;
import ru.citeck.ecos.model.domain.AssociationEntity;
import ru.citeck.ecos.model.domain.TypeEntity;
import ru.citeck.ecos.model.dto.TypeAssociationDto;
import ru.citeck.ecos.model.repository.TypeRepository;

@RequiredArgsConstructor
@Component
public class TypeAssociationConverter extends AbstractDtoConverter<TypeAssociationDto, AssociationEntity> {

    private final TypeRepository typeRepository;

    @Override
    public AssociationEntity dtoToEntity(TypeAssociationDto associationDto) {
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
    public TypeAssociationDto entityToDto(AssociationEntity associationEntity) {
        TypeAssociationDto assocDto = new TypeAssociationDto();
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
