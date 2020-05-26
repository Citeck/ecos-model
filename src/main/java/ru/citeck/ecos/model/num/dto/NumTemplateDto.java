package ru.citeck.ecos.model.num.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class NumTemplateDto {

    private String id;
    private String name;
    private String counterKey;

    public NumTemplateDto(String id) {
        this.id = id;
    }

    public NumTemplateDto(NumTemplateDto other) {
        this.id = other.id;
        this.name = other.name;
        this.counterKey = other.counterKey;
    }
}
