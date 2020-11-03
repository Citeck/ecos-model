package ru.citeck.ecos.model.domain.perms.api.records;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.domain.perms.dto.TypePermsMeta;
import ru.citeck.ecos.model.domain.perms.service.TypePermsService;
import ru.citeck.ecos.model.lib.type.dto.TypePermsDef;
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

    @Override
    public RecordsQueryResult<Object> queryLocalRecords(@NotNull RecordsQuery query, MetaField field) {

        if (query.getLanguage().equals(LANG_TYPE)) {
            TypeQuery typeQuery = query.getQuery(TypeQuery.class);
            TypePermsDef permsDef = permsService.getPermsForType(typeQuery.typeRef);
            return permsDef != null ? RecordsQueryResult.of(permsDef) : new RecordsQueryResult<>();
        }

        if (query.getLanguage().equals(PredicateService.LANGUAGE_PREDICATE)) {

            Predicate predicate = query.getQuery(Predicate.class);

            int max = query.getMaxItems();
            if (max <= 0) {
                max = 10000;
            }

            List<Sort.Order> order = query.getSortBy()
                .stream()
                .map(s -> {
                    String attribute = s.getAttribute();
                    if (RecordConstants.ATT_MODIFIED.equals(attribute)) {
                        attribute = "lastModifiedDate";
                    } else {
                        return Optional.<Sort.Order>empty();
                    }
                    return Optional.of(s.isAscending() ? Sort.Order.asc(attribute) : Sort.Order.desc(attribute));
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

            Collection<PermsRecord> perms = permsService.getAll(
                max,
                query.getSkipCount(),
                predicate,
                !order.isEmpty() ? Sort.by(order) : null
            ).stream().map(this::toRecord).collect(Collectors.toList());

            RecordsQueryResult<Object> permissions =  new RecordsQueryResult<>();
            permissions.setRecords(new ArrayList<>(perms));

            permissions.setTotalCount(permsService.getCount(predicate));
            return permissions;
        }

        return new RecordsQueryResult<>();
    }

    @NotNull
    @Override
    public List<Object> getLocalRecordsMeta(@NotNull List<RecordRef> list, @NotNull MetaField metaField) {
        return list.stream()
            .map(ref -> Optional.ofNullable(permsService.getPermsById(ref.getId())))
            .map(perms -> perms.isPresent() ? toRecord(perms.get()) : EmptyValue.INSTANCE)
            .collect(Collectors.toList());
    }

    @NotNull
    @Override
    public List<TypePermsDef.Builder> getValuesToMutate(@NotNull List<RecordRef> list) {
        return list.stream().map(l -> {
            TypePermsDef permsDef = permsService.getPermsById(l.getId());
            return permsDef != null ? permsDef.copy() : TypePermsDef.create().withId(l.getId());
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
            permsService.delete(r.getId());
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
        return new PermsRecord(permsDef, permsMeta);
    }

    @RequiredArgsConstructor
    public static class PermsRecord implements MetaValue {

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
                    return typePermsDef.getPermissions();
                case "attributes":
                    return typePermsDef.getAttributes();
            }
            return null;
        }
    }

    @Data
    public static class TypeQuery {
        private RecordRef typeRef;
    }
}
