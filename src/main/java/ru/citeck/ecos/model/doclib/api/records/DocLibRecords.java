package ru.citeck.ecos.model.doclib.api.records;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.context.lib.auth.AuthContext;
import ru.citeck.ecos.model.doclib.service.DocLibChildrenQuery;
import ru.citeck.ecos.model.doclib.service.DocLibService;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts;
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao;
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao;
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDao;
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao;
import ru.citeck.ecos.records3.record.dao.query.dto.query.QueryPage;
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery;
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes;
import ru.citeck.ecos.webapp.api.content.EcosContentApi;
import ru.citeck.ecos.webapp.api.entity.EntityRef;
import java.util.stream.Collectors;

@Component
public class DocLibRecords extends AbstractRecordsDao
        implements RecordsQueryDao, RecordMutateDao, RecordAttsDao {

    public static final String SOURCE_ID = "doclib";

    private static final String LANG_CHILDREN = "children";

    @Getter
    @NotNull
    private final DocLibService docLibService;

    private final EcosContentApi ecosContentApi;

    @Autowired
    public DocLibRecords(@NotNull DocLibService docLibService, EcosContentApi ecosContentApi) {
        this.docLibService = docLibService;
        this.ecosContentApi = ecosContentApi;
    }

    @Nullable
    @Override
    public Object getRecordAtts(@NotNull String recordId) {
        return new DocLibRecord(RecordRef.create(SOURCE_ID, recordId), this);
    }

    @NotNull
    @Override
    public String mutate(@NotNull LocalRecordAtts localRecordAtts) {
        String id = localRecordAtts.getId();
        if (!localRecordAtts.getAttributes().get("_type").getAs(EntityRef.class).getLocalId().equals("directory")) {
            EntityRef tempFile = AuthContext.runAsSystem(() -> ecosContentApi.uploadTempFile()
                .withMimeType(parseFileType(localRecordAtts))
                .withName(parseFileName(localRecordAtts))
                .writeContent(writer -> {
                    writer.writeBytes(parseBase64(localRecordAtts));
                    return null;
                }));
        }else {
            if (id.lastIndexOf(DocLibService.TYPE_DELIM) == id.length() - 1) {
                return docLibService.createEntity(localRecordAtts.getAttributes()).getId();
            } else {
                docLibService.mutate(localRecordAtts);
                return id;
            }
        }

        return id;
    }


    @Nullable
    @Override
    public RecsQueryRes<?> queryRecords(@NotNull RecordsQuery recordsQuery) {
        if (LANG_CHILDREN.equals(recordsQuery.getLanguage())) {
            DocLibChildrenQuery childrenQuery = recordsQuery.getQuery(DocLibChildrenQuery.class);
            return getChildren(childrenQuery, recordsQuery.getPage());
        }
        return null;
    }

    @NotNull
    @Override
    public String getId() {
        return SOURCE_ID;
    }

    private String parseFileName(LocalRecordAtts localRecordAtts){
        String fileName = localRecordAtts.getAttributes().get("_content").getValue().get(0).get("name").asText();
        String ext = fileName.substring(0, fileName.indexOf("."));
        return ext;
    }

    private String parseFileType(LocalRecordAtts localRecordAtts){
        return localRecordAtts.getAttributes().get("_content").getValue().findParent("type").get("type").asText();
    }

    private byte[] parseBase64(LocalRecordAtts localRecordAtts){
        String base64 = localRecordAtts.getAttributes().get("_content").getValue().get(0).get("url").asText().split(",")[1];
        byte[] fileBytes = javax.xml.bind.DatatypeConverter.parseBase64Binary(base64);
        return fileBytes;
    }

    public RecsQueryRes<DocLibRecord> getChildren(DocLibChildrenQuery query, QueryPage page) {

        RecsQueryRes<RecordRef> childrenRes = docLibService.getChildren(query, page);
        if (childrenRes.getRecords().isEmpty() && childrenRes.getTotalCount() == 0) {
            return new RecsQueryRes<>();
        }

        RecsQueryRes<DocLibRecord> result = new RecsQueryRes<>();
        result.setRecords(childrenRes.getRecords()
            .stream()
            .map(rec -> new DocLibRecord(rec, this))
            .collect(Collectors.toList())
        );
        result.setHasMore(childrenRes.getHasMore());
        result.setTotalCount(childrenRes.getTotalCount());

        return result;
    }


}
