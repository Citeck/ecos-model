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
                return TYPE_FORMKEY;
            case "name":
                return dto.getName();
            case "extId":
                return dto.getId();
            case "description":
                return dto.getDescription();
            case "tenant":
                return dto.getTenant();
            case "inheritActions":
                return dto.isInheritActions();
            case "parent":
            case RecordConstants.ATT_PARENT:
                return dto.getParent();
            case "actions":
            case RecordConstants.ATT_ACTIONS:
                return dto.getActions();
            case "associations":
                return dto.getAssociations();
        }
        return null;
    }

    @Override
    public Object getJson() {
        return dto;
    }

}
