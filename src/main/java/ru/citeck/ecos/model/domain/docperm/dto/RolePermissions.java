package ru.citeck.ecos.model.domain.docperm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RolePermissions {

    private String role;
    private Map<String, DocPerm> statuses = Collections.emptyMap();
}
