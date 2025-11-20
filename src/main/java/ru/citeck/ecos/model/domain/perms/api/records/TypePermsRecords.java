package ru.citeck.ecos.model.domain.perms.api.records;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.context.lib.auth.AuthContext;
import ru.citeck.ecos.context.lib.i18n.I18nContext;
import ru.citeck.ecos.model.domain.perms.dto.TypePermsMeta;
import ru.citeck.ecos.model.domain.perms.service.TypePermsService;
import ru.citeck.ecos.model.lib.permissions.dto.PermissionsDef;
import ru.citeck.ecos.model.lib.type.dto.TypePermsDef;
import ru.citeck.ecos.model.lib.utils.ModelUtils;
import ru.citeck.ecos.records2.RecordConstants;
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
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records3.record.atts.schema.ScalarType;
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class TypePermsRecords extends AbstractRecordsDao
    implements RecordAttsDao,
    RecordsQueryDao,
    RecordDeleteDao,
    RecordMutateDtoDao<TypePermsDef.Builder> {

    public static final String ID = "perms";

    public static final String LANG_TYPE = "type";

    private final TypePermsService permsService;
    private final RecordsService recordsService3;

    @Nullable
    @Override
    public Object queryRecords(@NotNull RecordsQuery recordsQuery) throws Exception {

        if (recordsQuery.getLanguage().equals(LANG_TYPE)) {
            TypeQuery typeQuery = recordsQuery.getQuery(TypeQuery.class);
            TypePermsDef permsDef = permsService.getPermsForType(typeQuery.typeRef);
            return permsDef != null ? RecsQueryRes.of(permsDef) : new RecsQueryRes<>();
        }

        if (recordsQuery.getLanguage().equals(PredicateService.LANGUAGE_PREDICATE)) {

            Predicate predicate = recordsQuery.getQuery(Predicate.class);

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
        TypePermsDef permsDef = permsService.getPermsById(recordId);
        if (permsDef == null) {
            return EmptyAttValue.INSTANCE;
        } else {
            return toRecord(permsDef);
        }
    }

    @Override
    public TypePermsDef.Builder getRecToMutate(@NotNull String recordId) throws Exception {
        TypePermsDef permsDef = permsService.getPermsById(recordId);
        return permsDef != null ? permsDef.copy() : TypePermsDef.create().withId(recordId);
    }

    @NotNull
    @Override
    public String saveMutatedRec(TypePermsDef.Builder builder) throws Exception {
        return permsService.save(builder.build()).getId();
    }

    @NotNull
    @Override
    public DelStatus delete(@NotNull String recordId) throws Exception {
        permsService.delete(recordId);
        return DelStatus.OK;
    }

    @Override
    public String getId() {
        return ID;
    }

    private PermsRecord toRecord(TypePermsDef permsDef) {
        TypePermsMeta permsMeta = permsService.getPermsMeta(permsDef.getId());
        if (permsMeta == null) {
            permsMeta = new TypePermsMeta(Instant.EPOCH);
        }
        return new PermsRecord(recordsService3, permsDef, permsMeta);
    }

    @RequiredArgsConstructor
    public static class PermsRecord implements AttValue {

        private final RecordsService recordsService;
        private final TypePermsDef typePermsDef;
        private final TypePermsMeta typePermsMeta;

        @Override
        public String getId() {
            return typePermsDef.getId();
        }

        @Override
        public Object getAtt(@NotNull String name) {
            return switch (name) {
                case RecordConstants.ATT_MODIFIED -> typePermsMeta.getModified();
                case "moduleId" -> typePermsDef.getId();
                case "typeRef" -> typePermsDef.getTypeRef();
                case "permissions" -> new PermsWrapper(typePermsDef.getPermissions());
                case "attributes" -> typePermsDef.getAttributes();
                default -> null;
            };
        }

        @Override
        public Object asJson() {
            return typePermsDef;
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

    // hack to allow standard checking of permissions._has.Write?bool
    @Data
    @SuppressWarnings("unused")
    @RequiredArgsConstructor
    public static class PermsWrapper {
        @AttName("...")
        private final PermissionsDef impl;

        public Boolean has(String name) {
            if ("WRITE".equalsIgnoreCase(name)) {
                return AuthContext.isRunAsSystemOrAdmin();
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

    @Data
    public static class TypeQuery {
        private EntityRef typeRef;
    }
}
