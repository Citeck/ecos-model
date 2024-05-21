package ru.citeck.ecos.model.domain.perms.api.records;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.context.lib.auth.AuthContext;
import ru.citeck.ecos.context.lib.i18n.I18nContext;
import ru.citeck.ecos.model.domain.perms.dto.TypePermsMeta;
import ru.citeck.ecos.model.domain.perms.service.TypePermsService;
import ru.citeck.ecos.model.lib.permissions.dto.PermissionsDef;
import ru.citeck.ecos.model.lib.type.dto.TypePermsDef;
import ru.citeck.ecos.model.utils.LegacyRecordsUtils;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.EmptyValue;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records2.request.delete.RecordsDelResult;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.request.result.RecordsResult;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.MutableRecordsLocalDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDao;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records3.record.atts.schema.ScalarType;
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class TypePermsRecords extends LocalRecordsDao
    implements LocalRecordsMetaDao<Object>,
    LocalRecordsQueryWithMetaDao<Object>,
    MutableRecordsLocalDao<TypePermsDef.Builder> {

    public static final String ID = "perms";

    public static final String LANG_TYPE = "type";

    private final TypePermsService permsService;
    private final RecordsService recordsService3;

    @Override
    public RecordsQueryResult<Object> queryLocalRecords(@NotNull RecordsQuery query, MetaField field) {

        if (query.getLanguage().equals(LANG_TYPE)) {
            TypeQuery typeQuery = query.getQuery(TypeQuery.class);
            TypePermsDef permsDef = permsService.getPermsForType(typeQuery.typeRef);
            return permsDef != null ? RecordsQueryResult.of(permsDef) : new RecordsQueryResult<>();
        }

        if (query.getLanguage().equals(PredicateService.LANGUAGE_PREDICATE)) {

            Predicate predicate = query.getQuery(Predicate.class);

            Collection<PermsRecord> perms = permsService.getAll(
                query.getMaxItems(),
                query.getSkipCount(),
                predicate,
                LegacyRecordsUtils.mapLegacySortBy(query.getSortBy())
            ).stream().map(this::toRecord).collect(Collectors.toList());

            RecordsQueryResult<Object> permissions = new RecordsQueryResult<>();
            permissions.setRecords(new ArrayList<>(perms));

            permissions.setTotalCount(permsService.getCount(predicate));
            return permissions;
        }

        return new RecordsQueryResult<>();
    }

    @NotNull
    @Override
    public List<Object> getLocalRecordsMeta(@NotNull List<EntityRef> list, @NotNull MetaField metaField) {
        return list.stream()
            .map(ref -> Optional.ofNullable(permsService.getPermsById(ref.getLocalId())))
            .map(perms -> perms.isPresent() ? toRecord(perms.get()) : EmptyValue.INSTANCE)
            .collect(Collectors.toList());
    }

    @NotNull
    @Override
    public List<TypePermsDef.Builder> getValuesToMutate(@NotNull List<EntityRef> list) {
        return list.stream().map(l -> {
            TypePermsDef permsDef = permsService.getPermsById(l.getLocalId());
            return permsDef != null ? permsDef.copy() : TypePermsDef.create().withId(l.getLocalId());
        }).collect(Collectors.toList());
    }

    @NotNull
    @Override
    public RecordsMutResult save(@NotNull List<TypePermsDef.Builder> list) {
        List<RecordMeta> result = new ArrayList<>();
        for (TypePermsDef.Builder config : list) {
            permsService.save(config.build());
            result.add(new RecordMeta(RecordRef.create("", config.getId())));
        }
        RecordsMutResult recordsMutResult = new RecordsMutResult();
        recordsMutResult.setRecords(result);
        return recordsMutResult;
    }

    @Override
    public RecordsDelResult delete(RecordsDeletion recordsDeletion) {
        List<RecordMeta> result = new ArrayList<>();
        recordsDeletion.getRecords().forEach(r -> {
            permsService.delete(r.getLocalId());
            result.add(new RecordMeta(r));
        });
        return new RecordsDelResult(new RecordsResult<>(result));
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
    public static class PermsRecord implements MetaValue {

        private final RecordsService recordsService;
        private final TypePermsDef typePermsDef;
        private final TypePermsMeta typePermsMeta;

        @Override
        public String getId() {
            return typePermsDef.getId();
        }

        @Override
        public Object getAttribute(@NotNull String name, @NotNull MetaField field) {
            switch (name) {
                case RecordConstants.ATT_MODIFIED:
                    return typePermsMeta.getModified();
                case "moduleId":
                    return typePermsDef.getId();
                case "typeRef":
                    return typePermsDef.getTypeRef();
                case "permissions":
                    return new PermsWrapper(typePermsDef.getPermissions());
                case "attributes":
                    return typePermsDef.getAttributes();
            }
            return null;
        }

        @Override
        public Object getJson() {
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
