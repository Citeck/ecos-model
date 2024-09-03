package ru.citeck.ecos.model.section.records.record;

import org.jetbrains.annotations.Nullable;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.context.lib.i18n.I18nContext;
import ru.citeck.ecos.model.section.dto.SectionDto;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records3.record.atts.value.AttValue;

public class SectionRecord implements AttValue {

    private final SectionDto dto;

    public SectionRecord(@Nullable SectionDto dto) {
        this.dto = dto != null ? dto : new SectionDto();
    }

    @Override
    public Object asJson() {
        return dto;
    }

    @Override
    public String getId() {
        return dto.getId();
    }

    @Override
    public String getDisplayName() {
        return MLText.getClosestValue(dto.getName(), I18nContext.getLocale(), dto.getId());
    }

    @Override
    public Object getAtt(String name) {
        return switch (name) {
            case "name" -> dto.getName();
            case "description" -> dto.getDescription();
            case "tenant" -> dto.getTenant();
            case "types" -> dto.getTypes();
            case "attributes" -> dto.getAttributes();
            case "moduleId" -> dto.getId();
            case RecordConstants.ATT_FORM_KEY -> "module_model/section";
            default -> null;
        };
    }
}
