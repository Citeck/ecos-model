package ru.citeck.ecos.model.domain.permissions.service.converter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.converter.AbstractDtoConverter;
import ru.citeck.ecos.model.domain.permissions.repo.AttributesPermissionEntity;
import ru.citeck.ecos.model.domain.permissions.dto.AttributesPermissionWithMetaDto;
import ru.citeck.ecos.model.domain.permissions.dto.RuleDto;
import ru.citeck.ecos.model.domain.permissions.repo.AttributesPermissionsRepository;
import ru.citeck.ecos.model.service.exception.TypeNotFoundException;
import ru.citeck.ecos.model.type.domain.TypeEntity;
import ru.citeck.ecos.model.type.records.dao.TypeRecordsDaoOld;
import ru.citeck.ecos.model.type.repository.TypeRepository;
import ru.citeck.ecos.records2.RecordRef;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Component
public class AttributesPermissionConverter extends AbstractDtoConverter<AttributesPermissionWithMetaDto, AttributesPermissionEntity> {

    private final TypeRepository typeRepository;
    private final AttributesPermissionsRepository attributesPermissionsRepository;

    @Override
    public AttributesPermissionEntity dtoToEntity(AttributesPermissionWithMetaDto dto) {

        AttributesPermissionEntity entity = new AttributesPermissionEntity();

        if (dto.getTypeRef() == null) {
            throw new IllegalArgumentException("Can't find type of permission attrs matrix");
        }

        Optional<TypeEntity> optionalType = typeRepository.findByExtId(dto.getTypeRef().getId());
        if (!optionalType.isPresent()) {
            throw new TypeNotFoundException(dto.getId());
        }

        entity.setType(optionalType.get());

        String extId = dto.getId();
        if (Strings.isBlank(extId)) {
            entity.setExtId(UUID.randomUUID().toString());
        } else {
            entity.setExtId(extId);
        }

        List<RuleDto> rules = dto.getRules();
        if (!CollectionUtils.isEmpty(rules)) {
            entity.setRules(Json.getMapper().toString(rules));
        }

        return entity;
    }

    @Override
    public AttributesPermissionWithMetaDto entityToDto(AttributesPermissionEntity entity) {
        AttributesPermissionWithMetaDto dto = new AttributesPermissionWithMetaDto();
        dto.setId(entity.getExtId());

        if (entity.getType() != null) {
            String typeId = entity.getType().getExtId();
            RecordRef typeRecordRef = RecordRef.create("emodel", TypeRecordsDaoOld.ID, typeId);
            dto.setTypeRef(typeRecordRef);
        } else {
            log.warn("Target type for permission attr matrix with id " + dto.getId() + " is null");
        }

        String rulesStr = entity.getRules();
        if (StringUtils.isNotBlank(rulesStr)) {
            try {
                dto.setRules(Json.getMapper().readList(rulesStr, RuleDto.class));
            } catch (RuntimeException e) {
                log.error("Cannot deserialize rules for permission entity with id: '" + entity.getId() +
                        "' Value: " + rulesStr);
            }
        }

        Optional<AttributesPermissionEntity> storedSection = attributesPermissionsRepository.findByExtId(entity.getExtId());
        storedSection.ifPresent(s -> entity.setId(s.getId()));

        dto.setCreated(entity.getCreatedDate());
        dto.setCreator(entity.getCreatedBy());
        dto.setModified(entity.getLastModifiedDate());
        dto.setModifier(entity.getLastModifiedBy());

        return dto;
    }
}
