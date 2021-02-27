package ru.citeck.ecos.model.association.converter;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.association.domain.AssociationEntity;
import ru.citeck.ecos.model.association.dto.AssociationDto;
import ru.citeck.ecos.model.service.exception.TypeNotFoundException;
import ru.citeck.ecos.model.type.api.records.TypeRecordsDao;
import ru.citeck.ecos.model.type.repository.TypeEntity;
import ru.citeck.ecos.model.type.repository.TypeRepository;
import ru.citeck.ecos.records2.RecordRef;

import java.util.UUID;

@RequiredArgsConstructor
@Component
public class AssociationConverter {

    @Value("${spring.application.name}")
    private String appName;

    private final TypeRepository typeRepository;

    public AssociationEntity dtoToEntity(TypeEntity source, AssociationDto associationDto) {

        AssociationEntity associationEntity = source.getAssociations()
            .stream()
            .filter(a -> associationDto.getId().equals(a.getExtId()))
            .findFirst()
            .orElse(null);

        if (associationEntity == null) {
            associationEntity = new AssociationEntity();
        }

        associationEntity.setExtId(associationDto.getId());
        associationEntity.setName(Json.getMapper().toString(associationDto.getName()));
        associationEntity.setDirection(associationDto.getDirection());
        associationEntity.setAttribute(associationDto.getAttribute());

        associationEntity.setSource(source);

        RecordRef targetRecordRef = associationDto.getTarget();
        if (source.getExtId().equals(targetRecordRef.getId())) {

            associationEntity.setTarget(source);

        } else {
            TypeEntity optionalTarget = typeRepository.findByExtId(targetRecordRef.getId());
            if (optionalTarget == null) {
                throw new TypeNotFoundException(targetRecordRef.getId());
            }
            associationEntity.setTarget(optionalTarget);
        }


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
