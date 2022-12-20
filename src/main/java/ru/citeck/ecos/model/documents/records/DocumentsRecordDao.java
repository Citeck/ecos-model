package ru.citeck.ecos.model.documents.records;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.predicate.model.Predicates;
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts;
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName;
import ru.citeck.ecos.records3.record.atts.schema.resolver.AttContext;
import ru.citeck.ecos.records3.record.atts.value.AttValue;
import ru.citeck.ecos.records3.record.atts.value.impl.AttValueDelegate;
import ru.citeck.ecos.records3.record.atts.value.impl.InnerAttValue;
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao;
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao;
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery;
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes;
import ru.citeck.ecos.webapp.api.apps.EcosRemoteWebAppsApi;
import ru.citeck.ecos.webapp.api.constants.AppName;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DocumentsRecordDao extends AbstractRecordsDao implements RecordsQueryDao {

    private final static String DOCUMENT_TYPES_LANGUAGE = "document-types";
    private final static String TYPES_DOCUMENTS_LANGUAGE = "types-documents";

    private final static String ALF_NODES_SOURCE_ID = AppName.ALFRESCO + EntityRef.APP_NAME_DELIMITER;
    private final static Map<String, String> SOURCE_ID_MAPPING;

    static {
        SOURCE_ID_MAPPING = new HashMap<>();
        SOURCE_ID_MAPPING.put(ALF_NODES_SOURCE_ID + "@", ALF_NODES_SOURCE_ID);
    }

    private final EcosRemoteWebAppsApi ecosWebAppsApi;

    @Nullable
    @Override
    public Object queryRecords(@NotNull RecordsQuery recordsQuery) {

        switch (recordsQuery.getLanguage()) {
            case DOCUMENT_TYPES_LANGUAGE:
                return getDocumentTypes(recordsQuery);
            case TYPES_DOCUMENTS_LANGUAGE:
                return getTypesDocuments(recordsQuery);
            default:
                return null;
        }
    }

    private Object getDocumentTypes(RecordsQuery recordsQuery) {

        if (!ecosWebAppsApi.isAppAvailable(AppName.ALFRESCO)) {
            return Collections.emptyList();
        }

        RecordRef recordRef = RecordRef.valueOf(recordsQuery.getQuery().get("recordRef").asText());

        if (!AppName.ALFRESCO.equals(recordRef.getAppName())) {
            return Collections.emptyList();
        }

        Map<String, String> contextAtts = AttContext.getInnerAttsMap();

        if (contextAtts.isEmpty()) {
            return recordsService.query(recordsQuery.copy().withSourceId("alfresco/documents").build());
        } else {
            RecsQueryRes<RecordAtts> queryRes =
                recordsService.query(recordsQuery.copy().withSourceId("alfresco/documents").build(), contextAtts, true);

            RecsQueryRes<Object> queryResWithAtts = new RecsQueryRes<>();
            queryResWithAtts.setHasMore(queryRes.getHasMore());
            queryResWithAtts.setTotalCount(queryRes.getTotalCount());
            queryResWithAtts.setRecords(getRecordsPostProcess(queryRes.getRecords()));

            return queryResWithAtts;
        }
    }

    private Object getTypesDocuments(RecordsQuery recordsQuery) {

        RecordRef recordRef = RecordRef.valueOf(recordsQuery.getQuery().get("recordRef").asText());
        List<String> typesRefs = recordsQuery.getQuery().get("types").asList(String.class);

        if (typesRefs.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, List<String>> sortedTypesRefs = new HashMap<>();
        List<RecordAtts> typeRefsAtts = recordsService.getAtts(typesRefs, Collections.singletonList("sourceId"));

        typeRefsAtts.forEach(type -> {
            String sourceId = type.get("sourceId").asText();
            sourceId = SOURCE_ID_MAPPING.getOrDefault(sourceId, sourceId);
            sortedTypesRefs.computeIfAbsent(sourceId, srcId -> new ArrayList<>())
                .add(TypeUtils.getTypeRef(type.getId().getLocalId()).toString());
        });


        Map<String, Object> resultRecordsByType = new HashMap<>();

        sortedTypesRefs.forEach((sourceId, typesList) -> {
            if (ALF_NODES_SOURCE_ID.equals(sourceId)) {
                DataValue query = recordsQuery.getQuery();
                query.set("types", typesList);

                Map<String, String> contextAtts = AttContext.getInnerAttsMap();

                RecsQueryRes<RecordAtts> queryRes = recordsService.query(
                    recordsQuery.copy()
                        .withSourceId(AppName.ALFRESCO + "/documents")
                        .withQuery(query)
                        .build(),
                    contextAtts,
                    true
                );

                List<RecordAtts> records = queryRes.getRecords();
                if (typesList.size() != records.size()) {
                    throw new RuntimeException(
                        "Invalid alfresco documents query response. " +
                        "Expected count: " + typesList.size() + " but received: " + records.size()
                    );
                }
                for (int idx = 0; idx < records.size(); idx++) {
                    resultRecordsByType.put(typesList.get(idx), records.get(idx));
                }
            } else {
                List<TypeDocumentsRecord> queryRes = getRecordsForTypes(sourceId, recordRef, typesList);
                for (int idx = 0; idx < typesList.size(); idx++) {
                    resultRecordsByType.put(typesList.get(idx), queryRes.get(idx));
                }
            }
        });

        RecsQueryRes<Object> queryResWithAtts = new RecsQueryRes<>();
        for (String typeRef : typesRefs) {
            queryResWithAtts.addRecord(resultRecordsByType.get(typeRef));
        }

        return queryResWithAtts;
    }

    private List<TypeDocumentsRecord> getRecordsForTypes(String sourceId,
                                                         RecordRef recordRef,
                                                         List<String> types) {
        RecordsQuery query = RecordsQuery.create()
            .withSourceId(sourceId)
            .withQuery(Predicates.and(
                Predicates.eq(RecordConstants.ATT_PARENT, recordRef.toString()),
                Predicates.in(RecordConstants.ATT_TYPE, types)
            ))
            .build();
        RecsQueryRes<DocumentRefWithType> documents = recordsService.query(query, DocumentRefWithType.class);

        List<TypeDocumentsRecord> typeDocumentsList = new ArrayList<>();

        types.forEach(type -> typeDocumentsList.add(new TypeDocumentsRecord(type, documents.getRecords().stream()
            .filter(doc -> doc.getType().toString().equals(type))
            .map(DocumentRefWithType::getRef)
            .collect(Collectors.toList()))));

        return typeDocumentsList;
    }

    private List<RecVal> getRecordsPostProcess(List<RecordAtts> attsFromTarget) {
        List<RecVal> result = new ArrayList<>();

        attsFromTarget.forEach(atts -> {
            InnerAttValue innerAttValue = new InnerAttValue(atts.getAtts().getData().asJson());
            result.add(new RecVal(atts.getId(), innerAttValue, atts.getAtts().asMap(String.class, Object.class)));
        });

        return result;
    }

    @NotNull
    @Override
    public String getId() {
        return "documents";
    }

    private static class RecVal extends AttValueDelegate implements AttValue {

        private final RecordRef id;
        AttValue base;
        Map<String, Object> atts;

        public RecVal(RecordRef id, AttValue base, Map<String, Object> atts) {
            super(base);
            this.base = base;
            this.id = id;
            this.atts = atts;
        }

        @Nullable
        @Override
        public Object getId() {
            return id;
        }
    }

    @Data
    private static class TypeDocumentsRecord {
        private final String id = UUID.randomUUID().toString();
        private final String type;
        private final List<EntityRef> documents;
    }

    @Data
    static class DocumentRefWithType {
        @AttName("?id")
        private EntityRef ref;
        @AttName("_type?id")
        private EntityRef type;
    }
}
