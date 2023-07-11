package ru.citeck.ecos.model.doclib.api.records;

import org.jetbrains.annotations.NotNull;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.model.doclib.service.DocLibChildrenQuery;
import ru.citeck.ecos.model.doclib.service.DocLibNodeInfo;
import ru.citeck.ecos.model.doclib.service.DocLibNodeType;
import ru.citeck.ecos.model.doclib.service.DocLibService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName;
import ru.citeck.ecos.webapp.api.entity.EntityRef;
import ru.citeck.ecos.records2.RecordConstants;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class DocLibRecord {

    private final RecordRef recordRef;

    private DocLibNodeInfo docLibNodeInfo;

    private final DocLibService docLibService;
    private final DocLibRecords docLibRecords;

    public DocLibRecord(@NotNull DocLibNodeInfo info, @NotNull DocLibRecords docLibRecords) {
        this(info.getRecordRef(), docLibRecords);
        this.docLibNodeInfo = info;
    }

    public DocLibRecord(@NotNull RecordRef recordRef, @NotNull DocLibRecords docLibRecords) {

        this.recordRef = recordRef;

        this.docLibRecords = docLibRecords;
        this.docLibService = docLibRecords.getDocLibService();
    }

    @AttName("?id")
    public RecordRef getId() {
        return recordRef;
    }

    public DocLibNodeType getNodeType() {
        return getDocLibNodeInfo().getNodeType();
    }

    public List<DocLibRecord> getChildren() {

        DocLibChildrenQuery query = new DocLibChildrenQuery();
        query.setParentRef(recordRef);
        query.setRecursive(false);
        query.setNodeType(null);

        return docLibService.getChildren(query, null).getRecords()
            .stream()
            .map(rec -> new DocLibRecord(rec, docLibRecords))
            .collect(Collectors.toList());
    }

    public List<DocLibRecord> getPath() {
        return docLibService.getPath(recordRef)
            .stream()
            .map(rec -> new DocLibRecord(rec, docLibRecords))
            .collect(Collectors.toList());
    }

    @AttName("_type")
    public EntityRef getTypeRef() {
        return getDocLibNodeInfo().getTypeRef();
    }

    public EntityRef getDocLibTypeRef() {
        return getDocLibNodeInfo().getDocLibTypeRef();
    }

    public Boolean getHasChildrenDirs() {
        return docLibService.hasChildrenDirs(recordRef);
    }

    @AttName("?disp")
    public String getDisplayName() {
        return getDocLibNodeInfo().getDisplayName();
    }


    @AttName(RecordConstants.ATT_MODIFIED)
    public Date getModified() {
        return getDocLibNodeInfo().getModified();
    }

    @AttName(RecordConstants.ATT_CREATED)
    public Date getCreated() {
        return getDocLibNodeInfo().getCreated();
    }

    public ObjectData getPreviewInfo() {
        return getDocLibNodeInfo().getPreviewInfo();
    }

    private DocLibNodeInfo getDocLibNodeInfo() {
        if (docLibNodeInfo == null) {
            docLibNodeInfo = docLibService.getDocLibNodeInfo(recordRef);
        }
        return docLibNodeInfo;
    }

    @AttName("?json")
    public DocLibNodeInfo getJson() {
        return getDocLibNodeInfo();
    }

}
