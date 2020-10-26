package ru.citeck.ecos.model.domain.perms.service;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.commons.json.JsonMapper;
import ru.citeck.ecos.model.domain.perms.repo.TypePermsEntity;
import ru.citeck.ecos.model.domain.perms.repo.TypePermsRepository;
import ru.citeck.ecos.model.lib.permissions.dto.PermissionsDef;
import ru.citeck.ecos.model.lib.permissions.dto.TypePermsDef;
import ru.citeck.ecos.records2.RecordRef;

import java.util.UUID;
import java.util.function.Consumer;

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
}
