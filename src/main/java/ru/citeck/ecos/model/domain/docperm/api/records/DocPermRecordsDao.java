package ru.citeck.ecos.model.domain.docperm.api.records;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.domain.docperm.dto.DocPerm;
import ru.citeck.ecos.model.domain.docperm.dto.DocPermConfig;
import ru.citeck.ecos.model.domain.docperm.dto.RolePermissions;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.EmptyValue;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.request.delete.RecordsDelResult;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.MutableRecordsLocalDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class DocPermRecordsDao extends LocalRecordsDao implements LocalRecordsMetaDao<Object>, MutableRecordsLocalDao<DocPermConfig> {

    @NotNull
    @Override
    public List<Object> getLocalRecordsMeta(@NotNull List<RecordRef> list, @NotNull MetaField metaField) {
        return list.stream().map(DocPermRecordsDao::generateNewPermissions).collect(Collectors.toList());
    }

    @NotNull
    @Override
    public List<DocPermConfig> getValuesToMutate(@NotNull List<RecordRef> list) {
        return list.stream().map(l -> {
            DocPermConfig docPermConfig = new DocPermConfig();
            docPermConfig.setId(l.getId());
            return docPermConfig;
        }).collect(Collectors.toList());
    }

    @NotNull
    @Override
    public RecordsMutResult save(@NotNull List<DocPermConfig> list) {
        List<RecordMeta> result = new ArrayList<>();
        for (DocPermConfig config : list) {
            if (RecordRef.isEmpty(config.getTypeRef())) {
                throw new IllegalStateException("TypeRef is a mandatory parameter!");
            }
            result.add(new RecordMeta(RecordRef.create("emodel", "docperm", config.getId())));
        }
        RecordsMutResult recordsMutResult = new RecordsMutResult();
        recordsMutResult.setRecords(result);
        return recordsMutResult;
    }

    @Override
    public RecordsDelResult delete(RecordsDeletion recordsDeletion) {
        return null;
    }

    public static Object generateNewPermissions(RecordRef ref) {

        if (!ref.getId().equals("perm-config-123")) {
            return EmptyValue.INSTANCE;
        }

        DocPermConfig permissions = new DocPermConfig();
        permissions.setId(ref.getId());
        permissions.setTypeRef(RecordRef.create("emodel", "type", "contract"));
        permissions.setPermissions(Arrays.asList(
            new RolePermissions("initiator", new HashMap<String, DocPerm>() {{
                put("new", DocPerm.NONE);
                put("draft", DocPerm.NONE);
                put("approve", DocPerm.WRITE);
                put("archive", DocPerm.READ);
            }}),
            new RolePermissions("approver", new HashMap<String, DocPerm>() {{
                put("new", DocPerm.READ);
                put("draft", DocPerm.READ);
                put("approve", DocPerm.READ);
                put("archive", DocPerm.NONE);
            }}),
            new RolePermissions("technologist", new HashMap<String, DocPerm>() {{
                put("new", DocPerm.WRITE);
                put("draft", DocPerm.WRITE);
                put("approve", DocPerm.WRITE);
                put("archive", DocPerm.WRITE);
            }})
        ));

        return permissions;
    }

    @Override
    public String getId() {
        return "docperm";
    }
}
