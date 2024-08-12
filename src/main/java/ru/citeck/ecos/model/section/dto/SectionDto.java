package ru.citeck.ecos.model.section.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.context.lib.i18n.I18nContext;
import ru.citeck.ecos.webapp.api.entity.EntityRef;
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName;

import java.util.HashSet;
import java.util.Set;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class SectionDto {

    private String id;
    private MLText name;
    private String description;
    private String tenant;
    private Set<EntityRef> types;

    private ObjectData attributes;

    public SectionDto(SectionDto dto) {
        this.name = dto.name;
        this.description = dto.description;
        this.tenant = dto.tenant;
        this.id = dto.id;
        this.attributes = dto.attributes != null ? dto.attributes.deepCopy() : null;
        if (dto.types != null) {
            this.types = new HashSet<>(dto.types);
        }
    }

    @AttName("?disp")
    @JsonIgnore
    public String getDisplayName() {
        return MLText.getClosestValue(name, I18nContext.getLocale());
    }
}
