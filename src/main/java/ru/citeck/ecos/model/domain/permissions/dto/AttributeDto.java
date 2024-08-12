package ru.citeck.ecos.model.domain.permissions.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(value = JsonInclude.Include.NON_EMPTY)
public class AttributeDto {

    private String name;
    private PermissionsDto permissions;

    public AttributeDto(AttributeDto dto) {
        this.name = dto.name;
        this.permissions = dto.permissions;
    }
}
