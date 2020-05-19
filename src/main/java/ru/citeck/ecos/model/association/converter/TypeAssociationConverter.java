package ru.citeck.ecos.model.association.converter;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.converter.AbstractDtoConverter;
import ru.citeck.ecos.model.association.domain.AssociationEntity;
import ru.citeck.ecos.model.association.dto.AssociationDto;
import ru.citeck.ecos.model.type.records.dao.TypeRecordsDao;
import ru.citeck.ecos.records2.RecordRef;

import java.util.UUID;

@RequiredArgsConstructor
@Component
public class TypeAssociationConverter extends AbstractDtoConverter<AssociationDto, AssociationEntity> {

    @Override
    public AssociationEntity dtoToEntity(AssociationDto associationDto) {

        AssociationEntity associationEntity = new AssociationEntity();

        associationEntity.setExtId(associationDto.getId());
        associationEntity.setName(Json.getMapper().toString(associationDto.getName()));
        associationEntity.setDirection(associationDto.getDirection());

        if (Strings.isBlank(associationEntity.getExtId())) {
            associationEntity.setExtId(UUID.randomUUID().toString());
        }

        return associationEntity;
    }

    @Override
    public AssociationDto entityToDto(AssociationEntity associationEntity) {

        AssociationDto assocDto = new AssociationDto();

        assocDto.setId(associationEntity.getExtId());
        assocDto.setName(Json.getMapper().read(associationEntity.getName(), MLText.class));
        assocDto.setDirection(associationEntity.getDirection());

        String targetTypeId = associationEntity.getTarget().getExtId();
        RecordRef targetTypeRecordRef = RecordRef.create("emodel", TypeRecordsDao.ID, targetTypeId);
        assocDto.setTarget(targetTypeRecordRef);

        return assocDto;
    }
}
