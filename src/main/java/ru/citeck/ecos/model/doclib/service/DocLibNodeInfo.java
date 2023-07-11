package ru.citeck.ecos.model.doclib.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.jetbrains.annotations.Nullable;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Date;

@Data
@AllArgsConstructor
public class DocLibNodeInfo {

    private RecordRef recordRef;
    private DocLibNodeType nodeType;
    private String displayName;
    private RecordRef typeRef;
    private RecordRef docLibTypeRef;
    private Date modified;
    private Date created;
    private String modifier;
    private String creator;

    @Nullable
    private ObjectData previewInfo;
}
