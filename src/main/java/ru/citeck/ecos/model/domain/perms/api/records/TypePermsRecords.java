package ru.citeck.ecos.model.domain.perms.api.records;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.domain.perms.service.TypePermsService;
import ru.citeck.ecos.model.lib.permissions.dto.TypePermsDef;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.EmptyValue;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
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

import java.util.ArrayList;
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

        return new RecordsQueryResult<>();
    }

    @NotNull
    @Override
    public List<Object> getLocalRecordsMeta(@NotNull List<RecordRef> list, @NotNull MetaField metaField) {
        return list.stream()
            .map(ref -> Optional.ofNullable(permsService.getPermsById(ref.getId())))
            .map(perms -> perms.isPresent() ? perms.get() : EmptyValue.INSTANCE)
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

    @Data
    public static class TypeQuery {
        private RecordRef typeRef;
    }
}
