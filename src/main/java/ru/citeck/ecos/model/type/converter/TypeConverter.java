package ru.citeck.ecos.model.type.converter;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.commons.json.JsonMapper;
import ru.citeck.ecos.model.association.converter.AssociationConverter;
import ru.citeck.ecos.model.association.domain.AssociationEntity;
import ru.citeck.ecos.model.association.dto.AssociationDto;
import ru.citeck.ecos.model.converter.AbstractDtoConverter;
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef;
import ru.citeck.ecos.model.lib.role.dto.RoleDef;
import ru.citeck.ecos.model.lib.status.dto.StatusDef;
import ru.citeck.ecos.model.lib.type.dto.DocLibDef;
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef;
import ru.citeck.ecos.model.type.domain.TypeEntity;
import ru.citeck.ecos.model.type.dto.CreateVariantDto;
import ru.citeck.ecos.model.type.dto.TypeWithMetaDto;
import ru.citeck.ecos.model.type.records.dao.TypeRecordsDao;
import ru.citeck.ecos.model.type.repository.TypeRepository;
import ru.citeck.ecos.records2.RecordRef;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class TypeConverter extends AbstractDtoConverter<TypeWithMetaDto, TypeEntity> {

    private static final String BASE_TYPE_ID = "base";

    private final TypeRepository typeRepository;
    private final AssociationConverter associationConverter;

    private final JsonMapper mapper = Json.getMapper();

    public TypeConverter(TypeRepository typeRepository,
                         AssociationConverter associationConverter) {

        this.typeRepository = typeRepository;
        this.associationConverter = associationConverter;
    }

    @Override
    public TypeEntity dtoToEntity(TypeWithMetaDto dto) {

        Optional<TypeEntity> storedType = typeRepository.findByExtId(dto.getId());
        TypeEntity typeEntity = storedType.orElseGet(TypeEntity::new);

        typeEntity.setSystem(dto.isSystem());
        typeEntity.setDashboardType(dto.getDashboardType());
        typeEntity.setSourceId(dto.getSourceId());

        String typeDtoId = dto.getId();
        if (Strings.isBlank(typeDtoId)) {
            typeEntity.setExtId(UUID.randomUUID().toString());
        } else {
            typeEntity.setExtId(typeDtoId);
        }
        typeEntity.setName(mapper.toString(dto.getName()));
        typeEntity.setDescription(mapper.toString(dto.getDescription()));
        typeEntity.setDispNameTemplate(mapper.toString(dto.getDispNameTemplate()));
        typeEntity.setInheritNumTemplate(dto.isInheritNumTemplate());
        typeEntity.setNumTemplateRef(RecordRef.toString(dto.getNumTemplateRef()));

        TypeModelDef modelDef;
        if (dto.getModel() == null) {
            modelDef = TypeModelDef.EMPTY;
        } else {
            modelDef = dto.getModel().copy()
                .withRoles(filterNotEmpty(dto.getModel().getRoles(), RoleDef::getId))
                .withStatuses(filterNotEmpty(dto.getModel().getStatuses(), StatusDef::getId))
                .withAttributes(filterNotEmpty(dto.getModel().getAttributes(), AttributeDef::getId))
                .build();
        }
        typeEntity.setModel(mapper.toString(modelDef));

        DocLibDef docLibDef;
        if (dto.getDocLib() == null) {
            docLibDef = DocLibDef.EMPTY;
        } else {
            docLibDef = dto.getDocLib().copy()
                .withDirTypeRef(dto.getDocLib().getDirTypeRef())
                .withFileTypeRefs(filterNotEmptyRefs(dto.getDocLib().getFileTypeRefs()))
                .build();
        }
        typeEntity.setDocLib(mapper.toString(docLibDef));

        typeEntity.setInheritActions(dto.isInheritActions());

        ObjectData attributes = dto.getAttributes() != null ? dto.getAttributes() : ObjectData.create();
        typeEntity.setAttributes(attributes.toString());

        ObjectData config = dto.getConfig() != null ? dto.getConfig() : ObjectData.create();
        typeEntity.setConfig(config.toString());

        typeEntity.setTenant(dto.getTenant());
        typeEntity.setConfigForm(RecordRef.toString(dto.getConfigFormRef()));
        typeEntity.setForm(RecordRef.toString(dto.getFormRef()));
        typeEntity.setInheritForm(dto.isInheritForm());

        typeEntity.setJournal(RecordRef.toString(dto.getJournalRef()));

        RecordRef parentRef = dto.getParentRef();
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

        typeEntity.setActions(mapper.toString(dto.getActions()));
        typeEntity.setCreateVariants(mapper.toString(dto.getCreateVariants()));

        if (dto.getAliases() != null) {
            typeEntity.setAliases(new HashSet<>(dto.getAliases()));
        } else {
            typeEntity.setAliases(Collections.emptySet());
        }

        Set<AssociationEntity> associations = dto.getAssociations().stream()
            .filter(a -> StringUtils.isNotBlank(a.getId()))
            .map(a -> associationConverter.dtoToEntity(typeEntity, a))
            .collect(Collectors.toSet());
        typeEntity.setAssociations(associations);

        checkCyclicDependencies(typeEntity);

        typeEntity.setSourceId(dto.getSourceId());

        return typeEntity;
    }

    private List<RecordRef> filterNotEmptyRefs(List<RecordRef> elements) {
        if (elements == null || elements.isEmpty()) {
            return elements;
        }
        return elements.stream().filter(RecordRef::isNotEmpty).collect(Collectors.toList());
    }

    private <T> List<T> filterNotEmpty(List<T> elements, Function<T, String> getter) {
        if (elements == null) {
            return Collections.emptyList();
        }
        return elements.stream()
            .filter(it -> StringUtils.isNotBlank(getter.apply(it)))
            .collect(Collectors.toList());
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
    public TypeWithMetaDto entityToDto(TypeEntity entity) {

        TypeWithMetaDto dto = new TypeWithMetaDto();

        dto.setSystem(Boolean.TRUE.equals(entity.getSystem()));
        dto.setDashboardType(entity.getDashboardType());
        dto.setId(entity.getExtId());
        dto.setName(mapper.read(entity.getName(), MLText.class));
        dto.setDescription(mapper.read(entity.getDescription(), MLText.class));
        dto.setDispNameTemplate(mapper.read(entity.getDispNameTemplate(), MLText.class));
        dto.setNumTemplateRef(RecordRef.valueOf(entity.getNumTemplateRef()));
        dto.setInheritNumTemplate(Boolean.TRUE.equals(entity.getInheritNumTemplate()));
        dto.setInheritForm(!Boolean.FALSE.equals(entity.getInheritForm()));
        dto.setInheritActions(entity.isInheritActions());
        dto.setFormRef(RecordRef.valueOf(entity.getForm()));
        dto.setJournalRef(RecordRef.valueOf(entity.getJournal()));
        dto.setTenant(entity.getTenant());
        dto.setConfigFormRef(RecordRef.valueOf(entity.getConfigForm()));
        dto.setConfig(mapper.read(entity.getConfig(), ObjectData.class));
        dto.setSourceId(entity.getSourceId());

        dto.setModel(mapper.read(entity.getModel(), TypeModelDef.class));
        if (dto.getModel() == null) {
            dto.setModel(TypeModelDef.EMPTY);
        }

        dto.setDocLib(mapper.read(entity.getDocLib(), DocLibDef.class));
        if (dto.getDocLib() == null) {
            dto.setDocLib(DocLibDef.EMPTY);
        }

        String attributesStr = entity.getAttributes();
        dto.setAttributes(mapper.read(attributesStr, ObjectData.class));

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
            dto.setParentRef(parentRecordRef);
        }

        List<AssociationDto> associationDtoSet = entity.getAssociations().stream()
            .map(associationConverter::entityToDto)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        dto.setAssociations(associationDtoSet);

        dto.setActions(mapper.read(entity.getActions(), RecordRefsList.class));
        if (dto.getActions() == null) {
            dto.setActions(Collections.emptyList());
        }
        dto.setCreateVariants(mapper.read(entity.getCreateVariants(), CreateVariantsList.class));
        if (dto.getCreateVariants() == null) {
            dto.setCreateVariants(Collections.emptyList());
        }

        dto.setAliases(new ArrayList<>(entity.getAliases()));

        dto.setCreated(entity.getCreatedDate());
        dto.setCreator(entity.getCreatedBy());
        dto.setModified(entity.getLastModifiedDate());
        dto.setModifier(entity.getLastModifiedBy());

        return dto;
    }

    public static class RecordRefsList extends ArrayList<RecordRef> {}

    public static class CreateVariantsList extends ArrayList<CreateVariantDto> {}
}
