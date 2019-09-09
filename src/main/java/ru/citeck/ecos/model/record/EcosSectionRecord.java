package ru.citeck.ecos.model.record;

import ru.citeck.ecos.model.dto.EcosSectionDto;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;

public class EcosSectionRecord implements MetaValue {

    private static final String SECTION_FORMKEY = "ecos_section";
    private final EcosSectionDto dto;

    public EcosSectionRecord(EcosSectionDto dto) {
        this.dto = dto;
    }

    @Override
    public Object getJson() {
        return dto;
    }

    @Override
    public String getId() {
        return dto.getUuid();
    }

    @Override
    public String getDisplayName() {
        String dispName = dto.getName();
        if (dispName == null) {
            dispName = dto.getUuid();
        }
        return dispName;
    }

    @Override
    public Object getAttribute(String name, MetaField field) {
        switch (name) {
            case RecordConstants.ATT_FORM_KEY:
                return SECTION_FORMKEY;
            case "name":
                return dto.getName();
            case "description":
                return dto.getDescription();
            case "tenant":
                return dto.getTenant();
            case "types":
                return dto.getTypes();
        }
        return null;
    }

}
