package ru.citeck.ecos.model.association.converter;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.association.service.AssociationService;
import ru.citeck.ecos.model.converter.AbstractDtoConverter;
import ru.citeck.ecos.model.association.domain.AssociationEntity;
import ru.citeck.ecos.model.association.dto.AssociationDto;
import ru.citeck.ecos.model.converter.DtoConverter;
import ru.citeck.ecos.model.service.exception.TypeNotFoundException;
import ru.citeck.ecos.model.type.domain.TypeEntity;
import ru.citeck.ecos.model.type.dto.TypeDto;
import ru.citeck.ecos.model.type.dto.TypeWithMetaDto;
import ru.citeck.ecos.model.type.records.dao.TypeRecordsDao;
import ru.citeck.ecos.model.type.repository.TypeRepository;
import ru.citeck.ecos.model.type.service.TypeService;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class AssociationConverter {

    private final TypeRepository typeRepository;

    public AssociationEntity dtoToEntity(TypeEntity source, AssociationDto associationDto) {
        AssociationEntity associationEntity = new AssociationEntity();

        associationEntity.setExtId(associationDto.getId());
        associationEntity.setName(Json.getMapper().toString(associationDto.getName()));
        associationEntity.setDirection(associationDto.getDirection());
        associationEntity.setAttribute(associationDto.getAttribute());

        associationEntity.setSource(source);

        RecordRef targetRecordRef = associationDto.getTarget();
        Optional<TypeEntity> optionalTarget = typeRepository.findByExtId(targetRecordRef.getId());
        if (!optionalTarget.isPresent()) {
            throw new TypeNotFoundException(targetRecordRef.getId());
        }
        associationEntity.setTarget(optionalTarget.get());

        // thinks that's better to do this as pre-persist action in entity
        if (Strings.isBlank(associationEntity.getExtId())) {
            associationEntity.setExtId(UUID.randomUUID().toString());
        }

        return associationEntity;
    }

    public AssociationDto entityToDto(AssociationEntity associationEntity) {

        AssociationDto assocDto = new AssociationDto();

        assocDto.setId(associationEntity.getExtId());
        assocDto.setName(Json.getMapper().read(associationEntity.getName(), MLText.class));
        assocDto.setDirection(associationEntity.getDirection());
        assocDto.setAttribute(associationEntity.getAttribute());

        String targetTypeId = associationEntity.getTarget().getExtId();
        RecordRef targetTypeRecordRef = RecordRef.create("emodel", TypeRecordsDao.ID, targetTypeId);
        assocDto.setTarget(targetTypeRecordRef);

        return assocDto;
    }
}
