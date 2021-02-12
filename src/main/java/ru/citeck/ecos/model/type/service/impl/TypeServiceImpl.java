package ru.citeck.ecos.model.type.service.impl;

import kotlin.Unit;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.commands.CommandsService;
import ru.citeck.ecos.commands.annotation.CommandType;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.association.dto.AssociationDto;
import ru.citeck.ecos.model.converter.DtoConverter;
import ru.citeck.ecos.model.lib.type.dto.CreateVariantDef;
import ru.citeck.ecos.model.lib.type.dto.TypeDef;
import ru.citeck.ecos.model.lib.type.repo.TypesRepo;
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils;
import ru.citeck.ecos.model.service.exception.ForgottenChildsException;
import ru.citeck.ecos.model.type.domain.TypeEntity;
import ru.citeck.ecos.model.type.dto.TypeDto;
import ru.citeck.ecos.model.type.dto.TypeWithMetaDto;
import ru.citeck.ecos.model.type.repository.TypeRepository;
import ru.citeck.ecos.model.type.service.TypeService;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.predicate.PredicateUtils;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records2.predicate.model.ValuePredicate;
import springfox.documentation.annotations.Cacheable;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TypeServiceImpl implements TypeService, TypesRepo {

    private final TypeRepository typeRepository;
    private final DtoConverter<TypeWithMetaDto, TypeEntity> typeConverter;

    private Consumer<TypeDto> onTypeChangedListener = dto -> {};

    private final Set<String> PROTECTED_TYPES = new HashSet<>(Arrays.asList(
        "base",
        "case",
        "document",
        "number-template",
        "type",
        "user-base",
        "file",
        "directory"
    ));

    @NotNull
    @Override
    public List<RecordRef> getChildren(@NotNull RecordRef recordRef) {

        TypeEntity typeEntity = typeRepository.findByExtId(recordRef.getId()).orElse(null) ;
        if (typeEntity == null) {
            return Collections.emptyList();
        }

        Set<TypeEntity> children = typeRepository.findAllByParent(typeEntity);

        return children.stream()
            .map(child -> TypeUtils.getTypeRef(child.getExtId()))
            .collect(Collectors.toList());
    }

    @Nullable
    @Override
    public TypeDef getTypeDef(@NotNull RecordRef recordRef) {
        TypeWithMetaDto typeDto = getByExtId(recordRef.getId());
        if (typeDto == null) {
            return null;
        }
        return TypeDef.create(b -> {
            b.withId(typeDto.getId());
            b.withModel(typeDto.getModel());
            b.withDocLib(typeDto.getDocLib());
            b.withInheritNumTemplate(typeDto.isInheritNumTemplate());
            b.withNumTemplateRef(typeDto.getNumTemplateRef());
            b.withParentRef(typeDto.getParentRef());
            return Unit.INSTANCE;
        });
    }

    @Override
    public List<TypeWithMetaDto> getAll(int max, int skip, Predicate predicate) {
        return getAll(max, skip, predicate, null);
    }

    @Override
    public List<TypeWithMetaDto> getAll(int max, int skip, Predicate predicate, Sort sort) {

        if (sort == null) {
            sort = Sort.by(Sort.Direction.DESC, "id");
        }

        PageRequest page = PageRequest.of(skip / max, max, sort);

        return typeRepository.findAll(toSpec(predicate), page)
            .stream()
            .map(typeConverter::entityToDto)
            .collect(Collectors.toList());
    }

    @Override
    public List<TypeWithMetaDto> getAll(int max, int skip) {

        PageRequest page = PageRequest.of(skip / max, max, Sort.by(Sort.Direction.DESC, "id"));

        return typeRepository.findAll(page)
            .stream()
            .map(typeConverter::entityToDto)
            .collect(Collectors.toList());
    }

    @Override
    public int getCount() {
        return (int) typeRepository.count();
    }

    @Override
    public int getCount(Predicate predicate) {
        Specification<TypeEntity> spec = toSpec(predicate);
        return spec != null ? (int) typeRepository.count(spec) : getCount();
    }

    @Override
    public void addListener(Consumer<TypeDto> onTypeChangedListener) {
        this.onTypeChangedListener = onTypeChangedListener;
    }

    @Cacheable("types")
    public Set<TypeWithMetaDto> getAll() {
        return typeRepository.findAll().stream()
            .map(typeConverter::entityToDto)
            .collect(Collectors.toSet());
    }

    @Override
    public RecordRef getConfigFormRef(String extId) {
        AtomicReference<RecordRef> result = new AtomicReference<>();
        forEachTypeInAscHierarchy(extId, dto -> {
            if (RecordRef.isNotEmpty(dto.getConfigFormRef())) {
                result.set(dto.getConfigFormRef());
                return true;
            }
            return false;
        });
        return result.get();
    }

    @Override
    public String getInhSourceId(String extId) {

        AtomicReference<String> result = new AtomicReference<>();

        forEachTypeInAscHierarchy(extId, dto -> {
            String sourceId = dto.getSourceId();
            if (StringUtils.isNotBlank(sourceId)) {
                result.set(sourceId);
                return true;
            }
            return false;
        });

        return result.get() == null ? "" : result.get();
    }

    @Override
    public DataValue getInhAttribute(String extId, String name) {

        AtomicReference<DataValue> result = new AtomicReference<>();

        forEachTypeInAscHierarchy(extId, type -> {

            if (type.getAttributes() == null) {
                return false;
            }

            DataValue value = type.getAttributes().get(name);
            if (value.isNotNull() && (!value.isTextual() || StringUtils.isNotBlank(value.asText()))) {
                result.set(value);
                return true;
            }
            return false;
        });

        DataValue resValue = result.get();
        if (resValue == null) {
            return DataValue.NULL;
        }
        return resValue;
    }

    @Override
    public List<CreateVariantDef> getCreateVariants(String extId) {

        List<CreateVariantDef> result = new ArrayList<>();

        forEachTypeInDescHierarchy(extId, type -> {

            List<CreateVariantDef> createVariants = type.getCreateVariants();
            if (createVariants == null) {
                createVariants = Collections.emptyList();
            }

            if (Boolean.TRUE.equals(type.getDefaultCreateVariant())) {
                List<CreateVariantDef> newVariants = new ArrayList<>();
                newVariants.add(CreateVariantDef.create()
                    .withId("DEFAULT")
                    .build());
                newVariants.addAll(createVariants);
                createVariants = newVariants;
            }

            createVariants.forEach(cv -> {

                CreateVariantDef.Builder variant = cv.copy();
                if (RecordRef.isEmpty(variant.getTypeRef())) {
                    variant.withTypeRef(TypeUtils.getTypeRef(type.getId()));
                }

                if (MLText.isEmpty(variant.getName()) && RecordRef.isNotEmpty(variant.getTypeRef())) {

                    TypeWithMetaDto typeDto = getByExtId(variant.getTypeRef().getId());
                    MLText typeName = null;
                    if (typeDto != null) {
                        typeName = typeDto.getName();
                        if (MLText.isEmpty(typeName)) {
                            typeName = new MLText(typeDto.getId());
                        }
                    }
                    if (typeName != null && !MLText.isEmpty(typeName)) {
                        variant.withName(typeName);
                    }
                }
                if (RecordRef.isEmpty(variant.getFormRef())) {
                    variant.withFormRef(getInhFormRef(variant.getTypeRef().getId()));
                }
                if (variant.getSourceId().isEmpty()) {
                    variant.withSourceId(getSourceIdForType(type));
                }
                if (RecordRef.isEmpty(variant.getPostActionRef())) {
                    variant.withPostActionRef(getPostCreateActionRef(type));
                }
                if (!variant.getSourceId().isEmpty()) {
                    result.add(variant.build());
                } else {
                    log.warn("Create variant without sourceId will be ignored: " + variant.build());
                }
            });
            return false;
        });

        return result;
    }

    private RecordRef getPostCreateActionRef(TypeDto type) {

        if (RecordRef.isNotEmpty(type.getPostCreateActionRef())) {
            return type.getPostCreateActionRef();
        }

        AtomicReference<RecordRef> result = new AtomicReference<>(RecordRef.EMPTY);

        forEachTypeInAscHierarchy(type.getId(), inhType -> {
            if (RecordRef.isNotEmpty(inhType.getPostCreateActionRef())) {
                result.set(inhType.getPostCreateActionRef());
                return true;
            }
            return false;
        });

        return result.get();
    }

    private String getSourceIdForType(TypeDto type) {

        if (StringUtils.isNotBlank(type.getSourceId())) {
            return type.getSourceId();
        }

        AtomicReference<String> result = new AtomicReference<>("");

        forEachTypeInAscHierarchy(type.getId(), inhType -> {
            if (StringUtils.isNotBlank(inhType.getSourceId())) {
                result.set(inhType.getSourceId());
                return true;
            }
            return false;
        });

        return result.get();
    }

    @Override
    public String getDashboardType(String extId) {

        AtomicReference<String> result = new AtomicReference<>();

        forEachTypeInAscHierarchy(extId, type -> {
            if (StringUtils.isNotBlank(type.getDashboardType())) {
                result.set(type.getDashboardType());
                return true;
            }
            return false;
        });

        return result.get();
    }

    @Override
    public List<TypeWithMetaDto> getParents(String extId) {

        List<TypeWithMetaDto> result = new ArrayList<>();
        forEachTypeInAscHierarchy(extId, type -> {
            if (!Objects.equals(type.getId(), extId)) {
                result.add(type);
            }
            return false;
        });

        return result;
    }

    @Override
    public List<TypeWithMetaDto> getChildren(String extId) {

        List<TypeWithMetaDto> result = new ArrayList<>();
        forEachTypeInDescHierarchy(extId, type -> {
            if (!Objects.equals(type.getId(), extId)) {
                result.add(type);
            }
            return false;
        });

        return result;
    }

    @Override
    public List<TypeWithMetaDto> expandTypes(Collection<RecordRef> typeRefs) {

        if (typeRefs == null || typeRefs.isEmpty()) {
            return Collections.emptyList();
        }

        List<TypeWithMetaDto> result = new ArrayList<>();
        Set<String> resultIdsSet = new HashSet<>();

        for (RecordRef typeRef : typeRefs) {
            forEachTypeInDescHierarchy(typeRef.getId(), type -> {
                if (resultIdsSet.add(type.getId())) {
                    result.add(type);
                }
                return false;
            });
        }

        return result;
    }

    @Override
    public RecordRef getInhFormRef(String extId) {

        AtomicReference<RecordRef> result = new AtomicReference<>();
        forEachTypeInAscHierarchy(extId, dto -> {
            if (RecordRef.isNotEmpty(dto.getFormRef())) {
                result.set(dto.getFormRef());
                return true;
            }
            return !dto.isInheritForm();
        });

        return result.get();
    }

    @Override
    public List<AssociationDto> getFullAssocs(String extId) {

        Map<String, AssociationDto> assocs = new TreeMap<>();
        forEachTypeInAscHierarchyInv(extId, dto -> {

            List<AssociationDto> typeAssocs = dto.getAssociations();
            if (typeAssocs != null) {
                for (AssociationDto assoc : typeAssocs) {
                    assocs.put(assoc.getId(), assoc);
                }
            }
            return false;
        });

        return new ArrayList<>(assocs.values());
    }

    private void forEachTypeInDescHierarchy(String extId, Function<TypeWithMetaDto, Boolean> action) {
        forEachTypeInDescHierarchy(typeRepository.findByExtId(extId).orElse(null), action);
    }

    private void forEachTypeInDescHierarchy(TypeEntity type, Function<TypeWithMetaDto, Boolean> action) {
        if (type == null) {
            return;
        }
        if (action.apply(typeConverter.entityToDto(type))) {
            return;
        }
        Set<TypeEntity> types = typeRepository.findAllByParent(type);
        types.forEach(t -> forEachTypeInDescHierarchy(t, action));
    }

    private void forEachTypeInAscHierarchyInv(String extId, Function<TypeDto, Boolean> action) {

        List<TypeDto> types = new ArrayList<>();
        forEachTypeInAscHierarchy(extId, dto -> {
            types.add(dto);
            return false;
        });

        for (int i = types.size() - 1; i >= 0; i--) {
            if (action.apply(types.get(i))) {
                break;
            }
        }
    }

    private void forEachTypeInAscHierarchy(String extId, Function<TypeWithMetaDto, Boolean> action) {

        TypeWithMetaDto type = getByExtId(extId);
        if (type == null || action.apply(type)) {
            return;
        }

        while (type != null) {

            RecordRef parentRef = type.getParentRef();

            if (parentRef != null) {
                type = getByExtId(parentRef.getId());
                if (type != null) {
                    if (action.apply(type)) {
                        return;
                    }
                }
            } else {
                type = null;
            }
        }
    }

    @Override
    public Set<TypeWithMetaDto> getAll(Collection<String> extIds) {
        return typeRepository.findAllByExtIds(new HashSet<>(extIds)).stream()
            .map(typeConverter::entityToDto)
            .collect(Collectors.toSet());
    }

    @Nullable
    @Override
    public TypeWithMetaDto getByExtId(String extId) {
        return typeRepository.findByExtId(extId).map(typeConverter::entityToDto).orElse(null);
    }

    @Override
    public TypeWithMetaDto getByExtIdOrNull(String extId) {
        return typeRepository.findByExtId(extId).map(typeConverter::entityToDto).orElse(null);
    }

    @Override
    public TypeWithMetaDto getOrCreateByExtId(String extId) {

        Optional<TypeEntity> byExtId = typeRepository.findByExtId(extId);

        return byExtId.map(typeConverter::entityToDto)
            .orElseGet(() -> {
                if ("base".equals(extId) || "user-base".equals(extId) || "type".equals(extId)) {
                    throw new IllegalStateException("Base type doesn't exists: '" + extId + "'");
                }

                TypeDto typeDto = new TypeDto();
                typeDto.setId(extId);
                typeDto.setInheritActions(true);
                typeDto.setParent(RecordRef.create("emodel", "type", "user-base"));
                typeDto.setName(new MLText(extId));

                return new TypeWithMetaDto(save(typeDto));
            });
    }

    public TypeDto getBaseType() {
        return typeRepository.findByExtId("base")
            .map(typeConverter::entityToDto)
            .orElseThrow(() -> new IllegalArgumentException("Base type doesn't exists"));
    }

    @Override
    @Transactional
    public void delete(String extId) {
        if (PROTECTED_TYPES.contains(extId)) {
            throw new RuntimeException("Type '" + extId + "' is protected");
        }
        Optional<TypeEntity> optional = typeRepository.findByExtId(extId);
        optional.ifPresent(e -> {
            if (e.getChildren().size() > 0) {
                throw new ForgottenChildsException();
            }
            typeRepository.deleteById(e.getId());
        });
    }

    @Override
    @Transactional
    public TypeWithMetaDto save(TypeDto dto) {

        TypeEntity aliasOwner = getAliasOwner(dto.getId());

        if (aliasOwner != null) {
            return typeConverter.entityToDto(aliasOwner);
        }

        TypeEntity entity = typeConverter.dtoToEntity(new TypeWithMetaDto(dto));
        entity = typeRepository.save(entity);
        updateModifiedTimeForLinkedTypes(dto.getId());

        removeAliasedTypes(entity);

        TypeWithMetaDto typeDto = typeConverter.entityToDto(entity);

        onTypeChangedListener.accept(typeDto);

        return typeDto;
    }

    private void updateModifiedTimeForLinkedTypes(@NotNull String typeId) {

        Function<TypeWithMetaDto, Boolean> action = type -> {
            if (!typeId.equals(type.getId())) {
                TypeEntity childEntity = typeRepository.findByExtId(type.getId()).orElse(null);
                if (childEntity != null) {
                    childEntity.setLastModifiedDate(Instant.now());
                    typeRepository.save(childEntity);
                }
            }
            return false;
        };

        forEachTypeInDescHierarchy(typeId, action);
        forEachTypeInAscHierarchy(typeId, action);
    }

    private void removeAliasedTypes(final TypeEntity entity) {

        Set<String> aliases = entity.getAliases();

        if (aliases == null || aliases.isEmpty()) {
            return;
        }

        aliases.forEach(alias -> {

            if (alias.equals("base") || alias.equals("user-base") || alias.equals("case")) {
                throw new IllegalArgumentException("You can't override base types by alias. Type: "
                    + entity.getExtId() + " Aliases: " + entity.getAliases());
            }

            TypeEntity typeToRemove = typeRepository.findByExtId(alias).orElse(null);
            if (typeToRemove != null) {
                moveChildren(typeToRemove, entity);
                typeRepository.delete(typeToRemove);
            }
        });
    }

    private TypeEntity getAliasOwner(String extId) {
        if (StringUtils.isBlank(extId)) {
            return null;
        }
        return typeRepository.findByContainsInAliases(extId)
            .orElse(null);
    }

    private void moveChildren(TypeEntity from, TypeEntity to) {
        Set<TypeEntity> children = from.getChildren();
        TypeEntity child;
        for (Iterator<TypeEntity> it = children.iterator(); it.hasNext(); ) {
            child = it.next();
            child.setParent(to);
            typeRepository.save(child);
            to.getChildren().add(child);
            it.remove();
        }
    }

    // todo: this method should be in ecos-records-spring
    private Specification<TypeEntity> toSpec(Predicate predicate) {

        if (predicate instanceof ValuePredicate) {

            ValuePredicate valuePred = (ValuePredicate) predicate;

            ValuePredicate.Type type = valuePred.getType();
            Object value = valuePred.getValue();
            String attribute = valuePred.getAttribute();

            if (RecordConstants.ATT_MODIFIED.equals(attribute)
                    && ValuePredicate.Type.GT.equals(type)) {

                Instant instant = Json.getMapper().convert(value, Instant.class);
                if (instant != null) {
                    return (root, query, builder) ->
                        builder.greaterThan(root.get("lastModifiedDate").as(Instant.class), instant);
                }
            }
        }

        PredicateDto predicateDto = PredicateUtils.convertToDto(predicate, PredicateDto.class);
        Specification<TypeEntity> spec = null;

        if (StringUtils.isNotBlank(predicateDto.name)) {
            spec = (root, query, builder) ->
                builder.like(builder.lower(root.get("name")), "%" + predicateDto.name.toLowerCase() + "%");
        }
        if (StringUtils.isNotBlank(predicateDto.moduleId)) {
            Specification<TypeEntity> idSpec = (root, query, builder) ->
                builder.like(builder.lower(root.get("extId")), "%" + predicateDto.moduleId.toLowerCase() + "%");
            spec = spec != null ? spec.or(idSpec) : idSpec;
        }

        if (predicateDto.system != null) {
            Specification<TypeEntity> systemSpec;
            if (!predicateDto.system) {
                systemSpec = (root, query, builder) -> builder.not(builder.equal(root.get("system"), true));
            } else {
                systemSpec = (root, query, builder) -> builder.equal(root.get("system"), true);
            }
            spec = spec != null ? spec.and(systemSpec) : systemSpec;
        }

        return spec;
    }

    @Data
    public static class PredicateDto {
        private String name;
        private String moduleId;
        private Boolean system;
    }
}
