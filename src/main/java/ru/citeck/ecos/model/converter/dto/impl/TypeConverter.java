package ru.citeck.ecos.model.converter.dto.impl;

import ecos.com.fasterxml.jackson210.core.JsonProcessingException;
import ecos.com.fasterxml.jackson210.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records2.objdata.ObjectData;
import ru.citeck.ecos.records2.scalar.MLText;
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
import ru.citeck.ecos.records2.utils.json.JsonUtils;

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

        typeEntity.setSystem(dto.isSystem());
        typeEntity.setDashboardType(dto.getDashboardType());

        String typeDtoId = dto.getId();
        if (Strings.isBlank(typeDtoId)) {
            typeEntity.setExtId(UUID.randomUUID().toString());
        } else {
            typeEntity.setExtId(typeDtoId);
        }
        if (dto.getName() != null) {
            typeEntity.setName(JsonUtils.toString(dto.getName()));
        }
        if (dto.getDescription() != null) {
            typeEntity.setDescription(JsonUtils.toString(dto.getDescription()));
        }
        typeEntity.setTenant(dto.getTenant());
        typeEntity.setInheritActions(dto.isInheritActions());

        ObjectData attributes = dto.getAttributes();
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

        checkCyclicDependencies(typeEntity);

        return typeEntity;
    }

    private void checkCyclicDependencies(TypeEntity entity) {

        String currentId = entity.getExtId();
        TypeEntity parent = entity.getParent();

        List<String> depsList = new ArrayList<>();
        depsList.add(currentId);

        while (parent != null) {
            depsList.add(parent.getExtId());
            if (Objects.equals(currentId, parent.getExtId())) {
                throw new IllegalStateException("Cyclic dependencies! " + depsList);
            }
            parent = parent.getParent();
        }
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

        dto.setSystem(Boolean.TRUE.equals(entity.getSystem()));
        dto.setDashboardType(entity.getDashboardType());
        dto.setId(entity.getExtId());
        dto.setName(JsonUtils.read(entity.getName(), MLText.class));
        dto.setDescription(JsonUtils.read(entity.getDescription(), MLText.class));
        dto.setInheritActions(entity.isInheritActions());
        dto.setTenant(entity.getTenant());
        dto.setForm(entity.getForm());

        String attributesStr = entity.getAttributes();
        if (StringUtils.isNotBlank(attributesStr)) {
            try {
                dto.setAttributes(JsonUtils.read(attributesStr, ObjectData.class));
            } catch (RuntimeException ioe) {
                log.error("Cannot deserialize attributes for type entity with id: '"
                    + entity.getId() + "' Str: " + attributesStr);
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
