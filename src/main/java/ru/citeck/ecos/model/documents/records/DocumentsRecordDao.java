package ru.citeck.ecos.model.documents.records;

import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.predicate.model.Predicates;
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts;
import ru.citeck.ecos.records3.record.atts.schema.resolver.AttContext;
import ru.citeck.ecos.records3.record.atts.value.AttValue;
import ru.citeck.ecos.records3.record.atts.value.impl.AttValueDelegate;
import ru.citeck.ecos.records3.record.atts.value.impl.InnerAttValue;
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao;
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao;
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery;
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes;
import ru.citeck.ecos.webapp.api.apps.EcosWebAppsApi;
import ru.citeck.ecos.webapp.api.constants.AppName;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class DocumentsRecordDao extends AbstractRecordsDao implements RecordsQueryDao {

    private final String DOCUMENT_TYPES_LANGUAGE = "document-types";
    private final String TYPES_DOCUMENTS_LANGUAGE = "types-documents";

    private EcosWebAppsApi ecosWebAppsApi;

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

        Map<String, String> contextAtts = AttContext.getInnerAttsMap();
        Map<String, List<String>> sortedTypesRefs = new HashMap<>();
        List<RecordAtts> typeRefsAtts = recordsService.getAtts(typesRefs, Collections.singletonList("sourceId"));

        typeRefsAtts.forEach(type -> {
            String sourceId = type.get("sourceId").asText();
            if (sortedTypesRefs.containsKey(sourceId)) {
                sortedTypesRefs.get(sourceId).add("emodel/" + type.getId());
            } else {
                sortedTypesRefs.put(sourceId, new ArrayList<>(Collections.singletonList("emodel/" + type.getId())));
            }
        });

        RecsQueryRes<Object> queryResWithAtts = new RecsQueryRes<>();

        sortedTypesRefs.forEach((k, v) -> {
            if ("alfresco/".equals(k)) {
                DataValue query = recordsQuery.getQuery();
                query.set("types", v);

                RecsQueryRes<RecordAtts> queryRes =
                    recordsService.query(
                        recordsQuery.copy()
                            .withSourceId("alfresco/documents")
                            .withQuery(query)
                            .build(),
                        contextAtts,
                        true);

                queryResWithAtts.addRecords(getRecordsPostProcess(queryRes.getRecords()));
            } else {
                List<TypeDocumentsRecord> queryRes = getRecordsTypes(k, recordRef, v);
                queryResWithAtts.addRecords(queryRes);
            }
        });

        return queryResWithAtts;
    }


    private List<TypeDocumentsRecord> getRecordsTypes(String sourceId,
                                                      RecordRef recordRef,
                                                      List<String> types) {

        Map<String, String> context = new LinkedHashMap<>();
        context.put("_type", "_type?id");

        RecordsQuery query = RecordsQuery.create()
            .withSourceId(sourceId)
            .withQuery(Predicates.and(
                Predicates.eq("_parent", recordRef.toString()),
                Predicates.in("_type", types)
            ))
            .build();
        RecsQueryRes<RecordAtts> documents = recordsService.query(query, context, true);

        List<TypeDocumentsRecord> typeDocumentsList = new ArrayList<>();

        types.forEach(type -> typeDocumentsList.add(new TypeDocumentsRecord(type, documents.getRecords().stream()
            .filter(doc -> doc.getAtt("_type").get("?id").asText().equals(type))
            .map(RecordAtts::getId)
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

    @Autowired
    public void setEcosWebAppsApi(EcosWebAppsApi ecosWebAppsApi) {
        this.ecosWebAppsApi = ecosWebAppsApi;
    }

    @NotNull
    @Override
    public String getId() {
        return "documents";
    }

    private static class RecVal extends AttValueDelegate implements AttValue {

        private RecordRef id;
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
        private final List<RecordRef> documents;
    }
}
