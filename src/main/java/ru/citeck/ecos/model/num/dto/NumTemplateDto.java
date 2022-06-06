package ru.citeck.ecos.model.num.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import ru.citeck.ecos.model.lib.num.dto.NumTemplateDef;

@Data
@NoArgsConstructor
public class NumTemplateDto {

    private String id;
    private String name;
    private String counterKey;

    public NumTemplateDto(String id) {
        this.id = id;
    }

    public NumTemplateDto(NumTemplateDef other) {
        this.id = other.getId();
        this.name = other.getName();
        this.counterKey = other.getCounterKey();
    }

    public NumTemplateDto(NumTemplateDto other) {
        this.id = other.id;
        this.name = other.name;
        this.counterKey = other.counterKey;
    }
}
