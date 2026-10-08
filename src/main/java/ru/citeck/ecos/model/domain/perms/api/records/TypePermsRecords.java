package ru.citeck.ecos.model.domain.perms.api.records;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.context.lib.auth.AuthContext;
import ru.citeck.ecos.context.lib.i18n.I18nContext;
import ru.citeck.ecos.model.domain.perms.dto.TypePermsRecordData;
import ru.citeck.ecos.model.domain.perms.service.TypePermsService;
import ru.citeck.ecos.model.lib.permissions.dto.PermissionsDef;
import ru.citeck.ecos.model.lib.type.dto.TypePermsDef;
import ru.citeck.ecos.model.lib.utils.ModelUtils;
import ru.citeck.ecos.model.lib.workspace.IdInWs;
import ru.citeck.ecos.model.lib.workspace.WorkspaceService;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.model.AndPredicate;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records3.record.atts.schema.ScalarType;
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName;
import ru.citeck.ecos.records3.record.atts.value.AttValue;
import ru.citeck.ecos.records3.record.atts.value.impl.EmptyAttValue;
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao;
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao;
import ru.citeck.ecos.records3.record.dao.delete.DelStatus;
import ru.citeck.ecos.records3.record.dao.delete.RecordDeleteDao;
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDtoDao;
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao;
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery;
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class TypePermsRecords extends AbstractRecordsDao
    implements RecordAttsDao,
    RecordsQueryDao,
    RecordDeleteDao,
    RecordMutateDtoDao<TypePermsRecords.MutRecord> {

    public static final String ID = "perms";

    public static final String LANG_TYPE = "type";

    private final TypePermsService permsService;
    private final RecordsService recordsService3;
    private final WorkspaceService workspaceService;

    @Nullable
    @Override
    public Object queryRecords(@NotNull RecordsQuery recordsQuery) throws Exception {

        if (recordsQuery.getLanguage().equals(LANG_TYPE)) {
            TypeQuery typeQuery = recordsQuery.getQuery(TypeQuery.class);
            TypePermsRecordData data = permsService.getRecordDataForType(typeQuery.typeRef);
            return data != null && permsService.canRead(data.getId().getWorkspace())
                ? RecsQueryRes.of(toRecord(data)) : new RecsQueryRes<>();
        }

        if (recordsQuery.getLanguage().equals(PredicateService.LANGUAGE_PREDICATE)) {

            AndPredicate predicate = new AndPredicate();
            predicate.addPredicate(recordsQuery.getQuery(Predicate.class));
            predicate.addPredicate(workspaceService.buildAvailableWorkspacesPredicate(
                AuthContext.getCurrentRunAsAuth(), recordsQuery.getWorkspaces()
            ));

            Collection<PermsRecord> perms = permsService.getAll(
                recordsQuery.getPage().getMaxItems(),
                recordsQuery.getPage().getSkipCount(),
                predicate,
                recordsQuery.getSortBy()
            ).stream().map(this::toRecord).collect(Collectors.toList());

            RecsQueryRes<Object> permissions = new RecsQueryRes<>();
            permissions.setRecords(new ArrayList<>(perms));

            permissions.setTotalCount(permsService.getCount(predicate));
            return permissions;
        }

        return new RecsQueryRes<>();
    }

    @Nullable
    @Override
    public Object getRecordAtts(@NotNull String recordId) throws Exception {
        IdInWs id = workspaceService.convertToIdInWs(recordId);
        TypePermsRecordData data = permsService.getRecordDataById(id);
        if (data == null || !permsService.canRead(data.getId().getWorkspace())) {
            return EmptyAttValue.INSTANCE;
        } else {
            return toRecord(data);
        }
    }

    @Override
    public MutRecord getRecToMutate(@NotNull String recordId) throws Exception {
        IdInWs id = workspaceService.convertToIdInWs(recordId);
        TypePermsRecordData data = permsService.getRecordDataById(id);
        if (data != null) {
            permsService.checkWrite(data.getId().getWorkspace());
            return new MutRecord(data);
        }
        return new MutRecord(id);
    }

    @NotNull
    @Override
    public String saveMutatedRec(MutRecord builder) throws Exception {
        String expectedWorkspace = builder.resolveExpectedWorkspace();
        TypePermsDef saved = permsService.save(builder.build(), expectedWorkspace);
        return workspaceService.convertToStrId(permsService.getId(saved));
    }

    @NotNull
    @Override
    public DelStatus delete(@NotNull String recordId) throws Exception {
        permsService.delete(workspaceService.convertToIdInWs(recordId));
        return DelStatus.OK;
    }

    @Override
    public String getId() {
        return ID;
    }

    private PermsRecord toRecord(TypePermsRecordData data) {
        return new PermsRecord(recordsService3, permsService, workspaceService, data.getDefinition(), data);
    }

    @RequiredArgsConstructor
    public static class PermsRecord implements AttValue {

        private final RecordsService recordsService;
        private final TypePermsService permsService;
        private final WorkspaceService workspaceService;
        private final TypePermsDef typePermsDef;
        private final TypePermsRecordData recordData;

        @Override
        public String getId() {
            return workspaceService.convertToStrId(recordData.getId());
        }

        @Override
        public Object getAtt(@NotNull String name) {
            String workspace = permsService.getWorkspace(typePermsDef);
            return switch (name) {
                case RecordConstants.ATT_MODIFIED -> recordData.getMeta().getModified();
                case "id", "moduleId" -> typePermsDef.getId();
                case "workspace" -> workspace;
                case "workspaceRef", RecordConstants.ATT_WORKSPACE -> EntityRef.create("emodel", "workspace", workspace);
                case "typeRef" -> typePermsDef.getTypeRef();
                case "permissions" -> new PermsWrapper(typePermsDef.getPermissions(), permsService.canWrite(recordData.getId().getWorkspace()));
                case "attributes" -> typePermsDef.getAttributes();
                default -> null;
            };
        }

        @Override
        public Object asJson() {
            String portableTypeId = workspaceService.replaceWsPrefixToCurrentWsPlaceholder(
                typePermsDef.getTypeRef().getLocalId()
            );
            EntityRef portableTypeRef = typePermsDef.getTypeRef().withLocalId(portableTypeId);
            return typePermsDef.copy().withTypeRef(portableTypeRef).build();
        }

        @Override
        public String getDisplayName() {
            String typeName = recordsService.getAtt(typePermsDef.getTypeRef(), ScalarType.DISP_SCHEMA).asText();
            if (I18nContext.RUSSIAN.equals(I18nContext.getLocale())) {
                return "Матрица прав для '" + typeName + "'";
            } else {
                return "Permissions matrix for '" + typeName + "'";
            }
        }

        @Override
        public EntityRef getType() {
            return ModelUtils.getTypeRef("type-perms");
        }
    }

    // Expose matrix access through the standard permissions._has attributes.
    @Data
    @SuppressWarnings("unused")
    @RequiredArgsConstructor
    public static class PermsWrapper {
        @AttName("...")
        private final PermissionsDef impl;
        private final boolean writable;

        public Boolean has(String name) {
            if ("WRITE".equalsIgnoreCase(name)) {
                return writable;
            }
            if ("READ".equalsIgnoreCase(name)) {
                return true;
            }
            return null;
        }

        public PermissionsDef getAsJson() {
            return impl;
        }
    }

    public class MutRecord {
        private final TypePermsDef.Builder builder;
        private final IdInWs recordId;
        private final boolean existing;
        private String ctxWorkspace;

        public MutRecord(TypePermsRecordData base) {
            builder = base.getDefinition().copy();
            recordId = base.getId();
            existing = true;
        }

        public MutRecord(IdInWs id) {
            builder = TypePermsDef.create().withId(id.getId());
            recordId = id;
            existing = false;
        }

        public String getId() {
            return builder.getId();
        }

        public void setId(String id) {
            builder.withId(id);
        }

        public void setTypeRef(EntityRef typeRef) {
            builder.withTypeRef(typeRef);
        }

        public void setPermissions(PermissionsDef permissions) {
            builder.withPermissions(permissions);
        }

        public void setAttributes(Map<String, PermissionsDef> attributes) {
            builder.withAttributes(attributes);
        }

        public TypePermsDef build() {
            return builder.build();
        }

        @JsonProperty(RecordConstants.ATT_WORKSPACE)
        public void setCtxWorkspace(String workspace) {
            ctxWorkspace = workspace == null ? null : EntityRef.valueOf(workspace).getLocalId();
        }

        @Nullable
        public String resolveExpectedWorkspace() {
            if (existing) {
                permsService.checkWrite(recordId.getWorkspace());
                // Existing references constrain edits and bind CURRENT_WS to their stored scope.
                if (recordId.getId().equals(getId())) {
                    return recordId.getWorkspace();
                }
            }
            if (!existing && !recordId.getWorkspace().isEmpty()) {
                return recordId.getWorkspace();
            }
            // A complete type ref is authoritative. Page context is needed only to resolve a portable placeholder.
            String typeId = builder.getTypeRef().getLocalId();
            String typeIdWithoutPlaceholder = workspaceService.replaceCurrentWsPlaceholderToWsPrefix(typeId, "");
            if (typeId.equals(typeIdWithoutPlaceholder)) {
                return null;
            }
            return ctxWorkspace;
        }
    }

    @Data
    public static class TypeQuery {
        private EntityRef typeRef;
    }
}
