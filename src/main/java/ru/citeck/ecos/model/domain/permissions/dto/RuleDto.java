package ru.citeck.ecos.model.domain.permissions.dto;

import ecos.com.fasterxml.jackson210.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Data
@NoArgsConstructor
@JsonInclude(value = JsonInclude.Include.NON_EMPTY)
public class RuleDto {

    private Collection roles;
    private Collection statuses;
    private ObjectData condition;
    private List<AttributeDto> attributes = new ArrayList<>();

    public RuleDto(RuleDto other) {
        RuleDto copy = Json.getMapper().copy(other);
        if (copy == null) {
            return;
        }
        this.roles = other.roles;
        this.statuses = other.statuses;
        this.condition = copy.condition;
        this.attributes = other.attributes;
    }
}
