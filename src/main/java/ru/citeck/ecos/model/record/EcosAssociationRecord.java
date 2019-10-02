package ru.citeck.ecos.model.record;

import ru.citeck.ecos.model.dto.EcosAssociationDto;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;

public class EcosAssociationRecord implements MetaValue {

    private static final String ASSOCIATION_FORMKEY = "ecos_association";
    private final EcosAssociationDto dto;

    public EcosAssociationRecord(EcosAssociationDto dto) {
        this.dto = dto;
    }

    @Override
    public String getId() {
        return dto.getId();
    }

    @Override
    public String getDisplayName() {
        String dispName = dto.getName();
        if (dispName == null) {
            dispName = dto.getId();
        }
        return dispName;
    }

    @Override
    public Object getAttribute(String name, MetaField field) {
        switch (name) {
            case RecordConstants.ATT_FORM_KEY:
                return ASSOCIATION_FORMKEY;
            case "name":
                return dto.getName();
            case "title":
                return dto.getTitle();
            case "source":
                return dto.getSourceType();
            case "target":
                return dto.getTargetType();
        }
        return null;
    }

    @Override
    public Object getJson() {
        return dto;
    }

}
