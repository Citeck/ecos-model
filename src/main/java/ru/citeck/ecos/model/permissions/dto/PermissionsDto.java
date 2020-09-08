package ru.citeck.ecos.model.permissions.dto;

import ecos.com.fasterxml.jackson210.annotation.JsonInclude;
import ecos.com.fasterxml.jackson210.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(value = JsonInclude.Include.NON_EMPTY)
public class PermissionsDto {

    @JsonProperty("Read")
    private Boolean read = true;
    @JsonProperty("Edit")
    private Boolean edit = true;
}
