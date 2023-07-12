package ru.citeck.ecos.model.doclib.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.model.doclib.api.records.DocLibRecords;
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts;
import ru.citeck.ecos.records3.record.atts.schema.ScalarType;
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName;
import ru.citeck.ecos.records3.record.dao.query.dto.query.QueryPage;
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes;
import ru.citeck.ecos.webapp.api.entity.EntityRef;
import ru.citeck.ecos.webapp.lib.model.type.registry.EcosTypesRegistry;

import java.util.*;

@Service
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class DocLibService {

    public static final String TYPE_DELIM = "$";

    private static final String PARENT_LOCAL_ID_ATT = "_parent?localId";

    private static final DocLibNodeInfo EMPTY_NODE = new DocLibNodeInfo(
        RecordRef.EMPTY,
        DocLibNodeType.FILE,
        "",
        RecordRef.EMPTY,
        RecordRef.EMPTY,
        null,
        null,
        null,
        null,
        null
    );

    private final EcosTypesRegistry ecosTypeService; //typesRegistry
    private final RecordsService recordsService;

    public void mutate(LocalRecordAtts record) {

        EntityId entityId = getEntityId(record.getId());

        ObjectData attributes = prepareForMutation(record.getAttributes());
        recordsService.mutate(RecordRef.valueOf(entityId.localId), attributes);
    }

    public RecordRef createEntity(ObjectData attributes) {

        attributes = prepareForMutation(attributes);
        String parent = attributes.get(RecordConstants.ATT_PARENT).asText();
        EntityId parentEntityId = getEntityId(RecordRef.valueOf(parent));

        RecordRef docLibTypeRef = parentEntityId.getTypeRef();
        if (EntityRef.isEmpty(docLibTypeRef)) {
            throw new IllegalStateException("Incorrect parent entity id: '" + parent + "'. Type info is missing.");
        }

        return recordsService.create(docLibTypeRef.getLocalId(), attributes);
    }

    private ObjectData prepareForMutation(ObjectData data) {

        ObjectData dataCopy = data.deepCopy();

        String dispAtt = ScalarType.DISP.getMirrorAtt();
        if (dataCopy.has(dispAtt)) {
            dataCopy.set("cm:title", dataCopy.get(dispAtt));
            dataCopy.remove(dispAtt);
        }
        if (dataCopy.has("cm:title") && !dataCopy.has("cm:name")) {
            dataCopy.set("cm:name", dataCopy.get("cm:title"));
        }
        String parent = dataCopy.get(RecordConstants.ATT_PARENT).asText();
        EntityId parentEntityId = getEntityId(RecordRef.valueOf(parent));

        RecordRef docLibTypeRef = parentEntityId.getTypeRef();
        if (EntityRef.isEmpty(docLibTypeRef)) {
            throw new IllegalStateException("Incorrect parent entity id: '" + parent + "'. Type info is missing.");
        }
        dataCopy.set(RecordConstants.ATT_PARENT_ATT, parentEntityId);

        return dataCopy;
    }

    public List<DocLibNodeInfo> getPath(RecordRef docLibRef) {

        List<DocLibNodeInfo> resultPath = new ArrayList<>();

        EntityId entityId = getEntityId(docLibRef);
        if (EntityRef.isEmpty(entityId.getTypeRef()) || StringUtils.isBlank(entityId.getLocalId())) {
            return resultPath;
        }

        resultPath.add(getDocLibNodeInfo(getEntityRef(entityId.getTypeRef(), "")));

        return resultPath;
    }

    public boolean hasChildrenDirs(RecordRef docLibRef) {

        DocLibChildrenQuery query = new DocLibChildrenQuery();
        query.setParentRef(docLibRef);
        query.setRecursive(false);
        query.setNodeType(DocLibNodeType.DIR);

        RecsQueryRes<RecordRef> queryRes = getChildren(query, new QueryPage(1, 0, RecordRef.EMPTY));
        return !queryRes.getRecords().isEmpty();
    }

    public DocLibNodeInfo getDocLibNodeInfo(@Nullable RecordRef docLibRef) {

        EntityId entityId = getEntityId(docLibRef);

        if (EntityRef.isEmpty(entityId.getTypeRef())) {
            return EMPTY_NODE;
        }

            return new DocLibNodeInfo(
                docLibRef,
                DocLibNodeType.DIR,
                "",
                RecordRef.EMPTY,
                entityId.getTypeRef(),
                null,
                null,
                null,
                null,
                null
            );
        }

    public RecsQueryRes<RecordRef> getChildren(DocLibChildrenQuery query, QueryPage page) {

        EntityId entityId = getEntityId(query.getParentRef());

        if (EntityRef.isEmpty(entityId.typeRef)) {
            return new RecsQueryRes<>();
        }
        if (page == null) {
            page = new QueryPage(1000, 0, RecordRef.EMPTY);
        }

        return null;
    }

    private RecordRef getEntityRef(RecordRef typeRef, String localId) {
        return RecordRef.create(DocLibRecords.SOURCE_ID, typeRef.getId() + TYPE_DELIM + localId);
    }

    @NotNull
    private EntityId getEntityId(RecordRef ref) {
        return getEntityId(ref != null ? ref.getId() : null);
    }

    @NotNull
    private EntityId getEntityId(@Nullable String refId) {

        if (StringUtils.isBlank(refId)) {
            return new EntityId(RecordRef.EMPTY, "");
        }

        if (!refId.contains(TYPE_DELIM)) {
            return new EntityId(RecordRef.EMPTY, "");
        }

        int delimIdx = refId.indexOf('$');
        if (delimIdx == 0) {
            return new EntityId(RecordRef.EMPTY, "");
        }

        String typeId = refId.substring(0, delimIdx);
        String localId = "";
        if (delimIdx < refId.length() - 1) {
            localId = refId.substring(delimIdx + 1);
        }

        return new EntityId(TypeUtils.getTypeRef(typeId), localId);
    }

    @Data
    public static class DocLibEntityInfo {
        @AttName("_type?id")
        private RecordRef typeRef;
        @AttName("?disp")
        private String displayName;
        @AttName("cm:modified")
        private Date modified;
        @AttName("cm:modifier")
        private String modifier;
        @AttName("cm:created")
        private Date created;
        @AttName("cm:creator")
        private String creator;
        private ObjectData previewInfo;
    }

    @Data
    @AllArgsConstructor
    private static class EntityId {
        private final RecordRef typeRef;
        private final String localId;
    }
}
