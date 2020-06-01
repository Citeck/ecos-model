package ru.citeck.ecos.model.type.service.impl;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.association.service.AssociationService;
import ru.citeck.ecos.model.converter.DtoConverter;
import ru.citeck.ecos.model.service.exception.ForgottenChildsException;
import ru.citeck.ecos.model.type.domain.TypeEntity;
import ru.citeck.ecos.model.type.dto.CreateVariantDto;
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
public class TypeServiceImpl implements TypeService {

    private final TypeRepository typeRepository;
    private final AssociationService associationService;
    private final DtoConverter<TypeWithMetaDto, TypeEntity> typeConverter;

    private Consumer<TypeDto> onTypeChangedListener = dto -> {};

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
    public List<CreateVariantDto> getCreateVariants(String extId) {

        Map<String, CreateVariantDto> result = new LinkedHashMap<>();

        forEachTypeInDescHierarchy(extId, type -> {
            List<CreateVariantDto> createVariants = type.getCreateVariants();
            if (createVariants != null) {
                createVariants.forEach(cv -> {
                    CreateVariantDto variant = new CreateVariantDto(cv);
                    if (!variant.getAttributes().has("_etype")) {
                        variant.getAttributes().set("_etype", "emodel/type@" + type.getId());
                    }
                    if (variant.getFormRef() == null) {
                        variant.setFormRef(type.getFormRef());
                    }
                    if (RecordRef.isEmpty(variant.getRecordRef())) {
                        if (StringUtils.isNotBlank(type.getSourceId())) {
                            variant.setRecordRef(RecordRef.valueOf(type.getSourceId() + "@"));
                        } else {
                            variant.setRecordRef(RecordRef.valueOf("dict@idocs:doc"));
                        }
                    }
                    result.put(variant.getId(), variant);
                });
            }
            return false;
        });

        return new ArrayList<>(result.values());
    }

    @Override
    public String getDashboardType(String extId) {

        AtomicReference<String> result = new AtomicReference<>();

        forEachTypeInHierarchy(extId, type -> {
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
        forEachTypeInHierarchy(extId, type -> {
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

    private void forEachTypeInHierarchy(String extId, Function<TypeWithMetaDto, Boolean> action) {

        TypeWithMetaDto type = getByExtId(extId);
        if (action.apply(type)) {
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

    @Override
    public TypeWithMetaDto getByExtId(String extId) {
        return typeRepository.findByExtId(extId).map(typeConverter::entityToDto)
            .orElseThrow(() -> new IllegalArgumentException("Type doesnt exists: " + extId));
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
        associationService.extractAndSaveAssocsFromType(new TypeWithMetaDto(dto));

        removeAliasedTypes(entity);

        TypeWithMetaDto typeDto = typeConverter.entityToDto(entity);

        onTypeChangedListener.accept(typeDto);
        return typeDto;
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

        return spec;
    }

    @Data
    public static class PredicateDto {
        private String name;
        private String moduleId;
    }
}
