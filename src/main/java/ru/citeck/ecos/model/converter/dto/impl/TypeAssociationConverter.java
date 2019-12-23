package ru.citeck.ecos.model.converter.dto.impl;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.converter.dto.AbstractDtoConverter;
import ru.citeck.ecos.model.dao.TypeRecordsDao;
import ru.citeck.ecos.model.domain.AssociationEntity;
import ru.citeck.ecos.model.dto.TypeAssociationDto;
import ru.citeck.ecos.records2.RecordRef;

import java.util.UUID;

@RequiredArgsConstructor
@Component
public class TypeAssociationConverter extends AbstractDtoConverter<TypeAssociationDto, AssociationEntity> {

    @Override
    public AssociationEntity dtoToEntity(TypeAssociationDto associationDto) {

        AssociationEntity associationEntity = new AssociationEntity();

        associationEntity.setExtId(associationDto.getId());
        handleExtId(associationEntity);

        associationEntity.setName(associationDto.getName());
        associationEntity.setDirection(associationDto.getDirection());

        return associationEntity;
    }

    private void handleExtId(AssociationEntity associationEntity) {
        if (Strings.isBlank(associationEntity.getExtId())) {
            associationEntity.setExtId(UUID.randomUUID().toString());
        }
    }

    @Override
    public TypeAssociationDto entityToDto(AssociationEntity associationEntity) {

        TypeAssociationDto assocDto = new TypeAssociationDto();

        assocDto.setId(associationEntity.getExtId());
        assocDto.setName(associationEntity.getName());
        assocDto.setDirection(associationEntity.getDirection());

        String targetTypeId = associationEntity.getTarget().getExtId();
        RecordRef targetTypeRecordRef = RecordRef.create(TypeRecordsDao.ID, targetTypeId);
        assocDto.setTargetType(targetTypeRecordRef);

        return assocDto;
    }
}
