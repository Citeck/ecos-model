package ru.citeck.ecos.model.domain.permissions.dto;

import ecos.com.fasterxml.jackson210.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.records2.RecordRef;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@JsonInclude(value = JsonInclude.Include.NON_EMPTY)
public class AttributesPermissionDto {

    @NotNull
    private String id;
    private RecordRef typeRef;
    private List<RuleDto> rules = new ArrayList<>();

    public AttributesPermissionDto(AttributesPermissionDto dto) {
        this.id = dto.id;
        this.typeRef = dto.typeRef;
        this.rules = DataValue.create(dto.rules).toList(RuleDto.class);
    }
}
