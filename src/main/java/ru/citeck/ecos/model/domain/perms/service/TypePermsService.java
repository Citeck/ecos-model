package ru.citeck.ecos.model.domain.perms.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.commons.json.JsonMapper;
import ru.citeck.ecos.model.domain.perms.repo.TypePermsEntity;
import ru.citeck.ecos.model.domain.perms.repo.TypePermsRepository;
import ru.citeck.ecos.model.lib.permissions.dto.PermissionsDef;
import ru.citeck.ecos.model.lib.permissions.dto.TypePermsDef;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.predicate.PredicateUtils;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records2.predicate.model.ValuePredicate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TypePermsService {

    private final TypePermsRepository repository;

    private final JsonMapper mapper = Json.getMapper();

    private Consumer<TypePermsDef> listener;

    @Nullable
    public TypePermsDef getPermsForType(RecordRef typeRef) {
        return toDto(repository.findByTypeRef(typeRef.toString()));
    }

    @Nullable
    public TypePermsDef getPermsById(String id) {
        return toDto(repository.findByExtId(id));
    }

    public List<TypePermsDef> getAll(int max, int skip, Predicate predicate, Sort sort) {

        if (sort == null) {
            sort = Sort.by(Sort.Direction.DESC, "id");
        }

        PageRequest page = PageRequest.of(skip / max, max, sort);

        return repository.findAll(toSpec(predicate), page)
            .stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    public long getCount(Predicate predicate) {
        Specification<TypePermsEntity> spec = toSpec(predicate);
        return spec != null ? repository.count(spec) : getCount();
    }

    public long getCount() {
        return repository.count();
    }

    @NotNull
    public TypePermsDef save(TypePermsDef permissions) {

        if (RecordRef.isEmpty(permissions.getTypeRef())) {
            throw new IllegalStateException("TypeRef is a mandatory parameter!");
        }

        TypePermsEntity entity = toEntity(permissions);
        entity = repository.save(entity);

        TypePermsDef resultPermissions = toDto(entity);
        if (resultPermissions == null) {
            throw new IllegalStateException("Record permissions conversion error. Permissions: " + entity);
        }

        if (listener != null) {
            listener.accept(resultPermissions);
        }

        return resultPermissions;
    }

    public void delete(String id) {
        TypePermsEntity typePerms = repository.findByExtId(id);
        if (typePerms != null) {
            repository.delete(typePerms);
        }
    }

    public void setListener(Consumer<TypePermsDef> listener) {
        this.listener = listener;
    }

    @Nullable
    private TypePermsDef toDto(@Nullable TypePermsEntity entity) {

        if (entity == null) {
            return null;
        }

        PermissionsDef permissionsDef = mapper.read(entity.getPermissions(), PermissionsDef.class);
        if (permissionsDef == null) {
            permissionsDef = PermissionsDef.EMPTY;
        }

        return TypePermsDef.create()
            .withId(entity.getExtId())
            .withTypeRef(RecordRef.valueOf(entity.getTypeRef()))
            .withAttributes(DataValue.create(entity.getAttributes()).asMap(String.class, PermissionsDef.class))
            .withPermissions(permissionsDef)
            .build();
    }

    private TypePermsEntity toEntity(TypePermsDef dto) {

        TypePermsEntity entity = null;
        if (StringUtils.isNotBlank(dto.getId())) {
            entity = repository.findByExtId(dto.getId());
        }
        if (entity == null) {
            entity = new TypePermsEntity();
            String id = dto.getId();
            if (StringUtils.isBlank(id)) {
                id = UUID.randomUUID().toString();
            }
            entity.setExtId(id);
        }

        entity.setAttributes(mapper.toString(dto.getAttributes()));
        entity.setPermissions(mapper.toString(dto.getPermissions()));
        entity.setTypeRef(dto.getTypeRef().toString());

        return entity;
    }

    // todo: this method should be in ecos-records-spring
    private Specification<TypePermsEntity> toSpec(Predicate predicate) {

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
        Specification<TypePermsEntity> spec = null;

        if (StringUtils.isNotBlank(predicateDto.typeRef)) {
            spec = (root, query, builder) ->
                builder.like(builder.lower(root.get("typeRef")), "%" + predicateDto.typeRef.toLowerCase() + "%");
        }
        if (StringUtils.isNotBlank(predicateDto.moduleId)) {
            Specification<TypePermsEntity> idSpec = (root, query, builder) ->
                builder.like(builder.lower(root.get("extId")), "%" + predicateDto.moduleId.toLowerCase() + "%");
            spec = spec != null ? spec.or(idSpec) : idSpec;
        }

        return spec;
    }

    @Data
    public static class PredicateDto {
        private String moduleId;
        private String typeRef;
    }
}
