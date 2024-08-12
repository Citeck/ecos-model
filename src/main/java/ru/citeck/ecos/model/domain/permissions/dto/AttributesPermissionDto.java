package ru.citeck.ecos.model.domain.permissions.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@JsonInclude(value = JsonInclude.Include.NON_EMPTY)
public class AttributesPermissionDto {

    @NotNull
    private String id;
    private EntityRef typeRef;
    private List<RuleDto> rules = new ArrayList<>();

    public AttributesPermissionDto(AttributesPermissionDto dto) {
        this.id = dto.id;
        this.typeRef = dto.typeRef;
        this.rules = DataValue.create(dto.rules).toList(RuleDto.class);
    }
}
