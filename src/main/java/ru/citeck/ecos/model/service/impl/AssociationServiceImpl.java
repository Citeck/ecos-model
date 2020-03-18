package ru.citeck.ecos.model.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.model.converter.dto.DtoConverter;
import ru.citeck.ecos.model.domain.AssociationEntity;
import ru.citeck.ecos.model.domain.TypeEntity;
import ru.citeck.ecos.model.dto.TypeDto;
import ru.citeck.ecos.model.eapps.listener.AssociationDto;
import ru.citeck.ecos.model.repository.AssociationRepository;
import ru.citeck.ecos.model.repository.TypeRepository;
import ru.citeck.ecos.model.service.AssociationService;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class AssociationServiceImpl implements AssociationService {

    private final AssociationRepository associationRepository;
    private final TypeRepository typeRepository;
    private final DtoConverter<AssociationDto, AssociationEntity> associationConverter;
    private final DtoConverter<TypeDto, TypeEntity> typeConverter;

    /*
     *  Note:
     *
     *  We use this, because needed to save assocs separately from types
     *  Sometimes we need to save type with assoc to itself
     */
    public void extractAndSaveAssocsFromType(TypeDto source) {

        Set<AssociationEntity> associationEntities = source.getAssociations().stream()
            .filter(a -> StringUtils.isNotBlank(a.getId()))
            .map(a -> {
                AssociationEntity assocEntity = associationConverter.dtoToEntity(a);
                TypeEntity sourceEntity = typeConverter.dtoToEntity(source);
                assocEntity.setSource(sourceEntity);
                assocEntity.setSourceId(sourceEntity.getId());

                RecordRef targetTypeRecordRef = a.getTarget();
                if (targetTypeRecordRef == null) {
                    throw new IllegalArgumentException("Target type is null");
                }

                String targetTypeId = targetTypeRecordRef.getId();
                TypeEntity targetTypeEntity = typeRepository.findByExtId(targetTypeId)
                    .orElseThrow(() -> new IllegalArgumentException("Target type doesnt exists: " + targetTypeId));
                assocEntity.setTarget(targetTypeEntity);

                return assocEntity;
            })
            .collect(Collectors.toSet());

        saveAll(associationEntities);
    }

    @Override
    public void saveAll(Set<AssociationEntity> associationEntities) {
        associationRepository.saveAll(associationEntities.stream().
            peek(assoc -> {
                if (Strings.isBlank(assoc.getExtId())) {
                    assoc.setExtId(UUID.randomUUID().toString());
                }
            }).collect(Collectors.toList()));
    }
}
