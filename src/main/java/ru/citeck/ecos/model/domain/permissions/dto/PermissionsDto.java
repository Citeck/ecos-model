package ru.citeck.ecos.model.domain.permissions.dto;

import ecos.com.fasterxml.jackson210.annotation.JsonFormat;
import ecos.com.fasterxml.jackson210.annotation.JsonIgnoreProperties;
import ecos.com.fasterxml.jackson210.annotation.JsonInclude;
import ecos.com.fasterxml.jackson210.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(value = JsonInclude.Include.NON_EMPTY)
public class PermissionsDto {

    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
    private Boolean read;

    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
    private Boolean edit;

    public PermissionsDto(PermissionsDto dto) {
        this.read = dto.read;
        this.edit = dto.edit;
    }
}
