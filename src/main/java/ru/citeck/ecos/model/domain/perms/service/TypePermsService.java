package ru.citeck.ecos.model.domain.perms.service;

import kotlin.Unit;
import kotlin.jvm.functions.Function2;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.data.entity.EntityMeta;
import ru.citeck.ecos.commons.data.entity.EntityWithMeta;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.commons.json.JsonMapper;
import ru.citeck.ecos.context.lib.auth.AuthRole;
import ru.citeck.ecos.model.domain.perms.dto.TypePermsMeta;
import ru.citeck.ecos.model.domain.perms.repo.TypePermsEntity;
import ru.citeck.ecos.model.domain.perms.repo.TypePermsRepository;
import ru.citeck.ecos.model.lib.permissions.dto.PermissionsDef;
import ru.citeck.ecos.model.lib.type.dto.TypePermsDef;
import ru.citeck.ecos.webapp.api.entity.EntityRef;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy;
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverter;
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverterFactory;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TypePermsService {

    private final TypePermsRepository repository;
    private final JsonMapper mapper = Json.getMapper();

    private final JpaSearchConverterFactory predicateToJpaConvFactory;
    private JpaSearchConverter<TypePermsEntity> jpaSearchConv;

    private Consumer<TypePermsDef> listener;

    private final List<Function2<EntityWithMeta<TypePermsDef>, EntityWithMeta<TypePermsDef>, Unit>> listeners = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void init() {
        List<TypePermsEntity> typePermsEntities = repository.findAll();
        typePermsEntities.sort(Comparator.comparing(TypePermsEntity::getLastModifiedDate).reversed());
        Set<String> uniqueEntities = new HashSet<>();
        for (TypePermsEntity entity : typePermsEntities) {
            if (!uniqueEntities.add(entity.getTypeRef())) {
                log.info("Entity with typeRef: {} will be deleted", entity.getTypeRef());
                repository.delete(entity);
            }
        }
        jpaSearchConv = predicateToJpaConvFactory.createConverter(TypePermsEntity.class).build();
    }

    @Nullable
    public TypePermsMeta getPermsMeta(String id) {
        TypePermsEntity entity = repository.findByExtId(id);
        if (entity != null) {
            return new TypePermsMeta(entity.getLastModifiedDate());
        }
        return null;
    }

    @Nullable
    public TypePermsDef getPermsForType(EntityRef typeRef) {
        return toDto(repository.findByTypeRef(typeRef.toString()));
    }

    @Nullable
    public TypePermsDef getPermsById(String id) {
        return toDto(repository.findByExtId(id));
    }

    public List<EntityWithMeta<TypePermsDef>> getAllWithMeta() {
        return repository.findAll()
            .stream()
            .map(this::toDtoWithMeta)
            .collect(Collectors.toList());
    }

    public List<TypePermsDef> getAll(int max, int skip, Predicate predicate, List<SortBy> sort) {

        return jpaSearchConv.findAll(repository, predicate, max, skip, sort)
            .stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    public long getCount(Predicate predicate) {
        return jpaSearchConv.getCount(repository, predicate);
    }

    public long getCount() {
        return repository.count();
    }

    @NotNull
    @Secured({AuthRole.SYSTEM, AuthRole.ADMIN})
    public TypePermsDef save(TypePermsDef permissions) {
        if (EntityRef.isEmpty(permissions.getTypeRef())) {
            throw new IllegalStateException("TypeRef is a mandatory parameter!");
        }

        TypePermsEntity entity = toEntity(permissions);
        EntityWithMeta<TypePermsDef> entityBefore = null;
        if (entity.getId() != null) {
            entityBefore = toDtoWithMeta(entity);
        }
        entity = repository.save(entity);

        EntityWithMeta<TypePermsDef> resultPermissions = toDtoWithMeta(entity);
        if (resultPermissions == null) {
            throw new IllegalStateException("Record permissions conversion error. Permissions: " + entity);
        }

        if (listener != null) {
            listener.accept(resultPermissions.getEntity());
        }

        for (Function2<EntityWithMeta<TypePermsDef>, EntityWithMeta<TypePermsDef>, Unit> listener : listeners) {
            listener.invoke(entityBefore, resultPermissions);
        }

        return resultPermissions.getEntity();
    }

    @Secured({AuthRole.SYSTEM, AuthRole.ADMIN})
    public void delete(String id) {
        TypePermsEntity typePerms = repository.findByExtId(id);
        if (typePerms != null) {
            EntityWithMeta<TypePermsDef> permsDefBefore = toDtoWithMeta(typePerms);
            repository.delete(typePerms);
            listeners.forEach(it -> it.invoke(permsDefBefore, null));
        }
    }

    public void setListener(Consumer<TypePermsDef> listener) {
        this.listener = listener;
    }

    public void addListener(Function2<EntityWithMeta<TypePermsDef>, EntityWithMeta<TypePermsDef>, Unit> listener) {
        this.listeners.add(listener);
    }

    @Nullable
    private TypePermsDef toDto(@Nullable TypePermsEntity entity) {
        return Optional.ofNullable(toDtoWithMeta(entity))
            .map(EntityWithMeta::getEntity)
            .orElse(null);
    }

    @Nullable
    private EntityWithMeta<TypePermsDef> toDtoWithMeta(@Nullable TypePermsEntity entity) {

        if (entity == null) {
            return null;
        }

        PermissionsDef permissionsDef = mapper.read(entity.getPermissions(), PermissionsDef.class);
        if (permissionsDef == null) {
            permissionsDef = PermissionsDef.EMPTY;
        }

        TypePermsDef typePermsDef = TypePermsDef.create()
            .withId(entity.getExtId())
            .withTypeRef(EntityRef.valueOf(entity.getTypeRef()))
            .withAttributes(DataValue.create(entity.getAttributes()).asMap(String.class, PermissionsDef.class))
            .withPermissions(permissionsDef)
            .build();

        EntityMeta meta = EntityMeta.create()
            .withCreated(entity.getCreatedDate())
            .withCreator(entity.getCreatedBy())
            .withModified(entity.getLastModifiedDate())
            .withModifier(entity.getLastModifiedBy())
            .build();

        return new EntityWithMeta<>(typePermsDef, meta);
    }

    private TypePermsEntity toEntity(TypePermsDef dto) {

        TypePermsEntity entity = repository.findByTypeRef(dto.getTypeRef().toString());
        if (entity != null && StringUtils.isNotBlank(dto.getId())) {
            entity.setExtId(dto.getId());
        }
        if (entity == null) {
            entity = new TypePermsEntity();
            String id = dto.getId();
            if (StringUtils.isBlank(id)) {
                id = dto.getTypeRef().getLocalId();
            }
            entity.setExtId(id);
        }

        entity.setAttributes(mapper.toString(dto.getAttributes()));
        entity.setPermissions(mapper.toString(dto.getPermissions()));
        entity.setTypeRef(dto.getTypeRef().toString());

        return entity;
    }

    @Data
    public static class PredicateDto {
        private String moduleId;
        private String typeRef;
    }
}
