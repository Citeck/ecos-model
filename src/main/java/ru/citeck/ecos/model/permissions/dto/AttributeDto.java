package ru.citeck.ecos.model.permissions.dto;

import ecos.com.fasterxml.jackson210.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.citeck.ecos.commons.data.ObjectData;

import java.util.Collection;

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
