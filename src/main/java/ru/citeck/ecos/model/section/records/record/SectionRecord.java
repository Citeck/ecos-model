package ru.citeck.ecos.model.section.records.record;

import org.jetbrains.annotations.Nullable;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.model.section.dto.SectionDto;
import ru.citeck.ecos.records2.QueryContext;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;

public class SectionRecord implements MetaValue {

    private final SectionDto dto;

    public SectionRecord(@Nullable SectionDto dto) {
        this.dto = dto != null ? dto : new SectionDto();
    }

    @Override
    public Object getJson() {
        return dto;
    }

    @Override
    public String getId() {
        return dto.getId();
    }

    @Override
    public String getDisplayName() {
        return MLText.getClosestValue(dto.getName(), QueryContext.getCurrent().getLocale(), dto.getId());
    }

    @Override
    public Object getAttribute(String name, MetaField field) {
        switch (name) {
            case "name":
                return dto.getName();
            case "description":
                return dto.getDescription();
            case "tenant":
                return dto.getTenant();
            case "types":
                return dto.getTypes();
            case "attributes":
                return dto.getAttributes();
            case "moduleId":
                return dto.getId();
            case RecordConstants.ATT_FORM_KEY:
                return "module_model/section";
        }
        return null;
    }
}
