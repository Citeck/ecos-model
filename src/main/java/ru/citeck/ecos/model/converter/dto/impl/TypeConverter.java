package ru.citeck.ecos.model.converter.dto.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.converter.dto.AbstractDtoConverter;
import ru.citeck.ecos.model.converter.dto.DtoConverter;
import ru.citeck.ecos.model.domain.AssociationEntity;
import ru.citeck.ecos.model.domain.TypeEntity;
import ru.citeck.ecos.model.dto.TypeDto;
import ru.citeck.ecos.model.eapps.listener.AssociationDto;
import ru.citeck.ecos.model.eapps.listener.CreateVariantDto;
import ru.citeck.ecos.model.repository.TypeRepository;
import ru.citeck.ecos.model.web.records.dao.TypeRecordsDao;
import ru.citeck.ecos.records2.RecordRef;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class TypeConverter extends AbstractDtoConverter<TypeDto, TypeEntity> {

    private static final String BASE_TYPE_ID = "base";

    private final TypeRepository typeRepository;
    private final DtoConverter<AssociationDto, AssociationEntity> associationConverter;

    public TypeConverter(TypeRepository typeRepository,
                         DtoConverter<AssociationDto, AssociationEntity> associationConverter) {

        this.typeRepository = typeRepository;
        this.associationConverter = associationConverter;
    }

    @Override
    public TypeEntity dtoToEntity(TypeDto dto) {

        Optional<TypeEntity> storedType = typeRepository.findByExtId(dto.getId());
        TypeEntity typeEntity = storedType.orElseGet(TypeEntity::new);

        typeEntity.setSystem(dto.isSystem());
        typeEntity.setDashboardType(dto.getDashboardType());

        String typeDtoId = dto.getId();
        if (Strings.isBlank(typeDtoId)) {
            typeEntity.setExtId(UUID.randomUUID().toString());
        } else {
            typeEntity.setExtId(typeDtoId);
        }
        typeEntity.setName(Json.getMapper().toString(dto.getName()));
        typeEntity.setDescription(Json.getMapper().toString(dto.getDescription()));
        typeEntity.setInheritActions(dto.isInheritActions());

        ObjectData attributes = dto.getAttributes();
        if (attributes == null) {
            attributes = new ObjectData();
        }
        typeEntity.setAttributes(attributes.toString());
        typeEntity.setForm(RecordRef.toString(dto.getForm()));
        typeEntity.setJournal(RecordRef.toString(dto.getJournal()));

        RecordRef parentRef = dto.getParent();
        String parentExtId;
        if (RecordRef.isEmpty(parentRef)) {
            if (!Objects.equals(dto.getId(), BASE_TYPE_ID)) {
                parentExtId = BASE_TYPE_ID;
            } else {
                parentExtId = null;
            }
        } else {
            parentExtId = parentRef.getId();
        }

        if (parentExtId != null) {
            Optional<TypeEntity> optionalParent = typeRepository.findByExtId(parentExtId);
            typeEntity.setParent(optionalParent.orElseThrow(() ->
                new IllegalStateException("Type is undefined: '" + parentExtId + "'")));
        }

        typeEntity.setActions(Json.getMapper().toString(dto.getActions()));
        typeEntity.setCreateVariants(Json.getMapper().toString(dto.getCreateVariants()));

        if (dto.getAliases() != null) {
            typeEntity.setAliases(new HashSet<>(dto.getAliases()));
        } else {
            typeEntity.setAliases(Collections.emptySet());
        }

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

    @Override
    public TypeDto entityToDto(TypeEntity entity) {

        TypeDto dto = new TypeDto();

        dto.setSystem(Boolean.TRUE.equals(entity.getSystem()));
        dto.setDashboardType(entity.getDashboardType());
        dto.setId(entity.getExtId());
        dto.setName(Json.getMapper().read(entity.getName(), MLText.class));
        dto.setDescription(Json.getMapper().read(entity.getDescription(), MLText.class));
        dto.setInheritActions(entity.isInheritActions());
        dto.setForm(RecordRef.valueOf(entity.getForm()));
        dto.setJournal(RecordRef.valueOf(entity.getJournal()));

        String attributesStr = entity.getAttributes();
        dto.setAttributes(Json.getMapper().read(attributesStr, ObjectData.class));

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

        Set<AssociationDto> associationDtoSet = entity.getAssocsToOthers().stream()
            .map(associationConverter::entityToDto)
            .collect(Collectors.toSet());
        dto.setAssociations(new ArrayList<>(associationDtoSet));

        dto.setActions(Json.getMapper().read(entity.getActions(), RecordRefsList.class));
        if (dto.getActions() == null) {
            dto.setActions(Collections.emptyList());
        }
        dto.setCreateVariants(Json.getMapper().read(entity.getCreateVariants(), CreateVariantsList.class));
        if (dto.getCreateVariants() == null) {
            dto.setCreateVariants(Collections.emptyList());
        }

        dto.setAliases(new ArrayList<>(entity.getAliases()));

        return dto;
    }

    public static class RecordRefsList extends ArrayList<RecordRef> {}

    public static class CreateVariantsList extends ArrayList<CreateVariantDto> {}
}
