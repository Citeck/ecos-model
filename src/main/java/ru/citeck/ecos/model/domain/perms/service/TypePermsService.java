package ru.citeck.ecos.model.domain.perms.service;

import kotlin.Unit;
import kotlin.jvm.functions.Function2;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.data.entity.EntityMeta;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.commons.json.JsonMapper;
import ru.citeck.ecos.context.lib.auth.AuthContext;
import ru.citeck.ecos.model.domain.perms.dto.TypePermsMeta;
import ru.citeck.ecos.model.domain.perms.dto.TypePermsRecordData;
import ru.citeck.ecos.model.domain.perms.repo.TypePermsEntity;
import ru.citeck.ecos.model.domain.perms.repo.TypePermsRepository;
import ru.citeck.ecos.model.lib.permissions.dto.PermissionsDef;
import ru.citeck.ecos.model.lib.type.dto.TypePermsDef;
import ru.citeck.ecos.model.lib.workspace.IdInWs;
import ru.citeck.ecos.model.lib.workspace.WorkspaceService;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy;
import ru.citeck.ecos.webapp.api.entity.EntityRef;
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverter;
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverterFactory;

import jakarta.annotation.PostConstruct;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TypePermsService {

    private final TypePermsRepository repository;
    private final WorkspaceService workspaceService;
    private final JsonMapper mapper = Json.getMapper();

    private final JpaSearchConverterFactory predicateToJpaConvFactory;
    private JpaSearchConverter<TypePermsEntity> jpaSearchConv;

    private Consumer<TypePermsDef> listener;

    private final List<Function2<TypePermsRecordData, TypePermsRecordData, Unit>> listeners = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void init() {
        jpaSearchConv = predicateToJpaConvFactory.createConverter(TypePermsEntity.class)
            .withAttMapping("id", "extId")
            .withAttMapping("moduleId", "extId")
            .withAttMapping(RecordConstants.ATT_MODIFIED, "lastModifiedDate")
            .build();
    }

    /** Old records kept their workspace prefix in extId, or only in typeRef. */
    @Transactional
    public void migrateLegacyPermissions() {
        List<TypePermsEntity> entities = repository.findAll();
        entities.sort(Comparator.comparing(TypePermsEntity::getLastModifiedDate).reversed());
        Set<IdInWs> typeKeys = new HashSet<>();
        Set<IdInWs> idKeys = new HashSet<>();
        Map<TypePermsEntity, IdInWs> updates = new LinkedHashMap<>();
        for (TypePermsEntity entity : entities) {
            IdInWs matrixId = workspaceService.convertToIdInWs(entity.getExtId());
            String workspace = resolveLegacyWorkspace(entity, matrixId);
            String extId = matrixId.getWorkspace().equals(workspace) ? matrixId.getId() : entity.getExtId();
            if (!typeKeys.add(IdInWs.create(workspace, entity.getTypeRef()))) {
                log.warn("Deleting duplicate permissions matrix: {}", entity.getExtId());
                repository.delete(entity);
                continue;
            }
            if (!idKeys.add(IdInWs.create(workspace, extId))) {
                String oldId = extId;
                extId += "-legacy-" + entity.getId();
                while (!idKeys.add(IdInWs.create(workspace, extId))) {
                    extId += "-legacy";
                }
                log.warn("Renaming legacy matrix '{}' to '{}' in workspace '{}' to preserve both types", oldId, extId, workspace);
            }
            if (!workspace.equals(entity.getWorkspace()) || !extId.equals(entity.getExtId())) {
                updates.put(entity, IdInWs.create(workspace, extId));
            }
        }
        repository.flush();
        updates.forEach((entity, id) -> {
            entity.setWorkspace(id.getWorkspace());
            entity.setExtId(id.getId());
            repository.save(entity);
        });
    }

    private String resolveLegacyWorkspace(TypePermsEntity entity, IdInWs matrixId) {
        if (!entity.getWorkspace().isEmpty()) {
            return entity.getWorkspace();
        }
        // Preserve the original WS even when the legacy matrix targets a global type.
        if (!matrixId.getWorkspace().isEmpty()) {
            return matrixId.getWorkspace();
        }
        EntityRef typeRef = EntityRef.valueOf(entity.getTypeRef());
        return workspaceService.convertToIdInWs(typeRef.getLocalId()).getWorkspace();
    }

    @Nullable
    public TypePermsMeta getPermsMeta(IdInWs id) {
        TypePermsEntity entity = findById(id);
        if (entity != null) {
            return new TypePermsMeta(entity.getLastModifiedDate());
        }
        return null;
    }

    @Nullable
    public TypePermsDef getPermsForType(EntityRef typeRef) {
        TypePermsRecordData data = getRecordDataForType(typeRef);
        return data != null ? data.getDefinition() : null;
    }

    @Nullable
    public TypePermsRecordData getRecordDataForType(EntityRef typeRef) {
        EntityRef ref = normalizeTypeRef(typeRef);
        String workspace = workspaceService.convertToIdInWs(ref.getLocalId()).getWorkspace();
        TypePermsEntity entity = repository.findByWorkspaceAndTypeRef(workspace, ref.toString());
        return entity != null && isApplicable(entity) ? toRecordData(entity) : null;
    }

    @Nullable
    public TypePermsDef getPermsById(IdInWs id) {
        return toDto(findById(id));
    }

    @Nullable
    public TypePermsRecordData getRecordDataById(IdInWs id) {
        return toRecordData(findById(id));
    }

    public List<TypePermsRecordData> getAllWithMeta() {
        return repository.findAll()
            .stream()
            .filter(this::isApplicable)
            .map(this::toRecordData)
            .collect(Collectors.toList());
    }

    public List<TypePermsRecordData> getAll(int max, int skip, Predicate predicate, List<SortBy> sort) {

        return jpaSearchConv.findAll(repository, predicate, max, skip, sort)
            .stream()
            .map(this::toRecordData)
            .collect(Collectors.toList());
    }

    public long getCount(Predicate predicate) {
        return jpaSearchConv.getCount(repository, predicate);
    }

    public long getCount() {
        return repository.count();
    }

    @NotNull
    @Transactional
    public TypePermsDef save(TypePermsDef permissions) {
        return save(permissions, null);
    }

    @NotNull
    @Transactional
    public TypePermsDef save(TypePermsDef permissions, @Nullable String expectedWorkspace) {
        if (EntityRef.isEmpty(permissions.getTypeRef())) {
            throw new IllegalStateException("TypeRef is a mandatory parameter!");
        }

        permissions = normalize(permissions, expectedWorkspace);
        IdInWs id = getId(permissions);
        checkWrite(id.getWorkspace());
        TypePermsEntity byId = findById(id);
        TypePermsEntity byType = repository.findByWorkspaceAndTypeRef(
            id.getWorkspace(), permissions.getTypeRef().toString()
        );
        if (byId != null && byType != null && !byId.getId().equals(byType.getId())) {
            throw new IllegalArgumentException("Matrix id is already used for another type: " + permissions.getId());
        }
        TypePermsEntity entity = byId != null ? byId : byType;
        TypePermsRecordData entityBefore = toRecordData(entity);
        if (entity == null) {
            entity = new TypePermsEntity();
        }
        entity.setWorkspace(id.getWorkspace());
        entity.setExtId(id.getId());
        entity.setTypeRef(permissions.getTypeRef().toString());
        entity.setAttributes(mapper.toString(permissions.getAttributes()));
        entity.setPermissions(mapper.toString(permissions.getPermissions()));
        entity = repository.save(entity);

        TypePermsRecordData resultPermissions = toRecordData(entity);
        if (resultPermissions == null) {
            throw new IllegalStateException("Record permissions conversion error. Permissions: " + entity);
        }

        if (listener != null) {
            listener.accept(resultPermissions.getDefinition());
        }

        for (Function2<TypePermsRecordData, TypePermsRecordData, Unit> listener : listeners) {
            listener.invoke(entityBefore, resultPermissions);
        }

        return resultPermissions.getDefinition();
    }

    @Transactional
    public void delete(IdInWs id) {
        TypePermsEntity typePerms = findById(id);
        if (typePerms != null) {
            checkWrite(typePerms.getWorkspace());
            TypePermsRecordData permsDefBefore = toRecordData(typePerms);
            repository.delete(typePerms);
            listeners.forEach(it -> it.invoke(permsDefBefore, null));
        }
    }

    public void setListener(Consumer<TypePermsDef> listener) {
        this.listener = listener;
    }

    public void addListener(Function2<TypePermsRecordData, TypePermsRecordData, Unit> listener) {
        this.listeners.add(listener);
    }

    @Nullable
    private TypePermsDef toDto(@Nullable TypePermsEntity entity) {
        return Optional.ofNullable(toRecordData(entity))
            .map(TypePermsRecordData::getDefinition)
            .orElse(null);
    }

    @Nullable
    private TypePermsRecordData toRecordData(@Nullable TypePermsEntity entity) {

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

        IdInWs id = IdInWs.create(entity.getWorkspace(), entity.getExtId());
        return new TypePermsRecordData(typePermsDef, id, meta);
    }

    public IdInWs getId(TypePermsDef def) {
        return IdInWs.create(getWorkspace(def), def.getId());
    }

    public String getWorkspace(TypePermsDef def) {
        return workspaceService.convertToIdInWs(def.getTypeRef().getLocalId()).getWorkspace();
    }

    public boolean canRead(String workspace) {
        return workspace.isEmpty() || AuthContext.isRunAsSystemOrAdmin()
            || workspaceService.isRunAsSystemOrWsSystem(workspace)
            || workspaceService.isUserMemberOf(AuthContext.getCurrentUser(), workspace);
    }

    public boolean canWrite(String workspace) {
        return workspaceService.isRunAsSystemOrWsSystem(workspace)
            || workspaceService.getArtifactsWritePermission(AuthContext.getCurrentUser(), workspace, "type-perms");
    }

    public void checkWrite(String workspace) {
        if (!canWrite(workspace)) {
            throw new IllegalStateException("Permission denied. You can't change permission matrices in workspace '" + workspace + "'");
        }
    }

    private TypePermsDef normalize(TypePermsDef def, @Nullable String expectedWorkspace) {
        expectedWorkspace = normalizeWorkspace(expectedWorkspace);
        IdInWs matrixId = workspaceService.convertToIdInWs(def.getId());
        String contextWorkspace;
        if (expectedWorkspace != null) {
            contextWorkspace = expectedWorkspace;
        } else if (!matrixId.getWorkspace().isEmpty()) {
            contextWorkspace = matrixId.getWorkspace();
        } else {
            contextWorkspace = getWorkspace(def);
        }

        String boundTypeId = workspaceService.replaceCurrentWsPlaceholderToWsPrefix(
            def.getTypeRef().getLocalId(), contextWorkspace
        );
        EntityRef typeRef = normalizeTypeRef(def.getTypeRef().withLocalId(boundTypeId));
        IdInWs typeId = workspaceService.convertToIdInWs(typeRef.getLocalId());
        boolean isTypeDefaultId = def.getId().equals(typeId.getId());
        boolean contextMismatch = expectedWorkspace != null && !expectedWorkspace.equals(typeId.getWorkspace());
        boolean idMismatch = !isTypeDefaultId && !matrixId.getWorkspace().isEmpty()
            && !matrixId.getWorkspace().equals(typeId.getWorkspace());
        if (contextMismatch || idMismatch) {
            throw new IllegalArgumentException("Permissions matrix and its type must belong to the same workspace: " + typeRef);
        }

        boolean hasUnknownIdPrefix = def.getId().contains(":") && matrixId.getWorkspace().isEmpty();
        if (hasUnknownIdPrefix && !isTypeDefaultId) {
            // A namespaced default id is local in both global and workspace scopes.
            throw new IllegalArgumentException("Unknown workspace prefix in matrix id: " + def.getId());
        }
        String localId;
        if (StringUtils.isBlank(def.getId())) {
            localId = typeId.getId();
        } else if (isTypeDefaultId) {
            localId = def.getId();
        } else {
            localId = matrixId.getId();
        }
        return def.copy().withId(localId).withTypeRef(typeRef).build();
    }

    @Nullable
    private String normalizeWorkspace(@Nullable String workspace) {
        if (workspace == null) {
            return null;
        }
        String localId = EntityRef.valueOf(workspace).getLocalId();
        return workspaceService.isWorkspaceWithGlobalEntities(localId) ? "" : localId;
    }

    private EntityRef normalizeTypeRef(EntityRef ref) {
        if (EntityRef.isEmpty(ref) || (!ref.getAppName().isEmpty() && !"emodel".equals(ref.getAppName()))
            || !"type".equals(ref.getSourceId())) {
            throw new IllegalArgumentException("Expected an emodel/type reference: " + ref);
        }
        return ref.withAppName("emodel");
    }

    private TypePermsEntity findById(IdInWs id) {
        String workspace = normalizeWorkspace(id.getWorkspace());
        return repository.findByWorkspaceAndExtId(workspace, id.getId());
    }

    private boolean isApplicable(TypePermsEntity entity) {
        return isApplicable(toRecordData(entity));
    }

    public boolean isApplicable(TypePermsRecordData data) {
        try {
            normalize(data.getDefinition(), data.getId().getWorkspace());
            return true;
        } catch (IllegalArgumentException e) {
            log.warn("Ignoring invalid permissions matrix '{}' in workspace '{}': {}",
                data.getId().getId(), data.getId().getWorkspace(), e.getMessage());
            return false;
        }
    }

    @Data
    public static class PredicateDto {
        private String moduleId;
        private String typeRef;
    }
}
