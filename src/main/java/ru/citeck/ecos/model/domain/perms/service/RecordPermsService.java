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
import ru.citeck.ecos.model.lib.permissions.dto.RecordPermsDef;
import ru.citeck.ecos.records2.RecordRef;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecordPermsService {

    private final TypePermsRepository repository;

    private final JsonMapper mapper = Json.getMapper();

    @Nullable
    public RecordPermsDef getPermsForType(RecordRef typeRef) {
        return toDto(repository.findByTypeRef(typeRef.toString()));
    }

    @Nullable
    public RecordPermsDef getPermsById(String id) {
        return toDto(repository.findByExtId(id));
    }

    @NotNull
    public RecordPermsDef save(RecordPermsDef permissions) {

        if (RecordRef.isEmpty(permissions.getTypeRef())) {
            throw new IllegalStateException("TypeRef is a mandatory parameter!");
        }

        TypePermsEntity entity = toEntity(permissions);
        entity = repository.save(entity);

        RecordPermsDef def = toDto(entity);
        if (def == null) {
            throw new IllegalStateException("Record permissions conversion error. Permissions: " + entity);
        }
        return def;
    }

    @Nullable
    private RecordPermsDef toDto(@Nullable TypePermsEntity entity) {

        if (entity == null) {
            return null;
        }

        PermissionsDef permissionsDef = mapper.read(entity.getPermissions(), PermissionsDef.class);
        if (permissionsDef == null) {
            permissionsDef = PermissionsDef.EMPTY;
        }

        return RecordPermsDef.create()
            .withId(entity.getExtId())
            .withTypeRef(RecordRef.valueOf(entity.getTypeRef()))
            .withAttributes(DataValue.create(entity.getAttributes()).asMap(String.class, PermissionsDef.class))
            .withPermissions(permissionsDef)
            .build();
    }

    private TypePermsEntity toEntity(RecordPermsDef dto) {

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
