package ru.citeck.ecos.model.converter.dto.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
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
import ru.citeck.ecos.model.dto.TypeCreateVariantDto;
import ru.citeck.ecos.model.dto.TypeDto;
import ru.citeck.ecos.model.repository.TypeRepository;
import ru.citeck.ecos.records2.RecordRef;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class TypeConverter extends AbstractDtoConverter<TypeDto, TypeEntity> {

    private static final String BASE_TYPE_ID = "base";

    private final TypeRepository typeRepository;
    private final DtoConverter<TypeAssociationDto, AssociationEntity> associationConverter;
    private final DtoConverter<TypeCreateVariantDto, String> typeCreateVariantConverter;
    private final ObjectMapper objectMapper;

    public TypeConverter(TypeRepository typeRepository,
                         DtoConverter<TypeAssociationDto, AssociationEntity> associationConverter,
                         DtoConverter<TypeCreateVariantDto, String> typeCreateVariantConverter) {
        this.typeRepository = typeRepository;
        this.associationConverter = associationConverter;
        this.typeCreateVariantConverter = typeCreateVariantConverter;
        this.objectMapper = new ObjectMapper();
    }

    /*
    *   Note:
    *
    *   Associations logic moved to 'extractAndSaveAssocsFromType' method AssociationServiceImpl.class
    *   We cant and dont need to handle assocs to other types here.
    */
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

        ObjectNode attributes = dto.getAttributes();
        if (attributes != null) {
            typeEntity.setAttributes(attributes.toString());
        }

        if (StringUtils.isNotBlank(dto.getForm())) {
            typeEntity.setForm(dto.getForm());
        }

        RecordRef parentRef = dto.getParent();
        String parentExtId = null;
        if (parentRef == null || Strings.isBlank(parentRef.getId())) {
            if (!Objects.equals(dto.getId(), BASE_TYPE_ID)) {
                parentExtId = BASE_TYPE_ID;
            }
        } else {
            parentExtId = parentRef.getId();
        }

        if (parentExtId != null) {
            Optional<TypeEntity> optionalParent = typeRepository.findByExtId(parentExtId);
            optionalParent.ifPresent(typeEntity::setParent);
        }

        //  checking for existing in DB
        Optional<TypeEntity> storedType = typeRepository.findByExtId(typeEntity.getExtId());
        storedType.ifPresent(t -> {
            typeEntity.setId(t.getId());
        });

        //  actions
        List<TypeActionEntity> actionEntities = dto.getActions().stream()
            .filter(a -> StringUtils.isNotBlank(a.getId()))
            .map(a -> new TypeActionEntity(typeEntity, a.toString()))
            .collect(Collectors.toList());
        typeEntity.addActions(actionEntities);

        // create variants
        Set<TypeCreateVariantDto> createVariantDTOs = dto.getCreateVariants();
        Set<String> createVariantsStrings = createVariantDTOs.stream()
            .map(typeCreateVariantConverter::dtoToEntity)
            .collect(Collectors.toSet());
        String createVariantsStr = convertListOfStringsToContent(createVariantsStrings);
        typeEntity.setCreateVariants(createVariantsStr);

        return typeEntity;
    }

    private String convertListOfStringsToContent(Set<String> strings) {
        if (CollectionUtils.isNotEmpty(strings)) {
            try {
                return objectMapper.writeValueAsString(strings);
            } catch (JsonProcessingException jpe) {
                log.error("Cannot create solid string from multiple", jpe);
            }
        }
        return null;
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

        String attributesStr = entity.getAttributes();
        if (StringUtils.isNotBlank(attributesStr)) {
            try {
                ObjectNode attributes = objectMapper.readValue(attributesStr, ObjectNode.class);
                dto.setAttributes(attributes);
            } catch (IOException ioe) {
                log.error("Cannot deserialize attributes for type entity with id:" + entity.getId());
            }
        }

        TypeEntity parent = entity.getParent();
        String parentExtId = null;
        if (parent == null) {
            if (!Objects.equals(entity.getExtId(), BASE_TYPE_ID)) {
                parentExtId = BASE_TYPE_ID;
            }
        } else {
            parentExtId = parent.getExtId();
        }
        if (parentExtId != null) {
            RecordRef parentRecordRef = RecordRef.create("emodel", TypeRecordsDao.ID, parentExtId);
            dto.setParent(parentRecordRef);
        }

        Set<TypeAssociationDto> associationDtoSet = entity.getAssocsToOthers().stream()
            .map(associationConverter::entityToDto)
            .collect(Collectors.toSet());
        dto.setAssociations(associationDtoSet);

        Set<ModuleRef> actionsModuleRefs = entity.getActions().stream()
            .map(a -> ModuleRef.valueOf(a.getActionId()))
            .collect(Collectors.toSet());
        dto.setActions(actionsModuleRefs);

        Set<String> createVariantsStrings = convertContentToListOfString(entity.getCreateVariants());
        Set<TypeCreateVariantDto> createVariants = createVariantsStrings.stream()
            .map(typeCreateVariantConverter::entityToDto)
            .collect(Collectors.toSet());
        dto.setCreateVariants(createVariants);

        return dto;
    }

    @SuppressWarnings("unchecked")
    private Set<String> convertContentToListOfString(String content) {
        if (content != null) {
            try {
                return objectMapper.readValue(content, Set.class);
            } catch (IOException ioe) {
                log.error("Cannot convert content to list of strings");
            }
        }
        return Collections.emptySet();
    }
}
