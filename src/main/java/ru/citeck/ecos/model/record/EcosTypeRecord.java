package ru.citeck.ecos.model.record;

import ru.citeck.ecos.model.dto.EcosTypeDto;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;


public class EcosTypeRecord implements MetaValue {

    private static final String TYPE_FORMKEY = "ecos_type";
    private final EcosTypeDto dto;

    public EcosTypeRecord(EcosTypeDto dto) {
        this.dto = dto;
    }

    @Override
    public String getId() {
        return dto.getExtId();
    }

    @Override
    public String getDisplayName() {
        String dispName = dto.getName();
        if (dispName == null) {
            dispName = dto.getExtId();
        }
        return dispName;
    }

    @Override
    public Object getAttribute(String name, MetaField field) {
        switch (name) {
            case RecordConstants.ATT_FORM_KEY:
                return TYPE_FORMKEY;
            case "name":
                return dto.getName();
            case "extId":
                return dto.getExtId();
            case "description":
                return dto.getDescription();
            case "tenant":
                return dto.getTenant();
            case "parent":
                return dto.getParent();
            case "sections":
                return dto.getSections();
        }
        return null;
    }

    @Override
    public Object getJson() {
        return dto;
    }

}
