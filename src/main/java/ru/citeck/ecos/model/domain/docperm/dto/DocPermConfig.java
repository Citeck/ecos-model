package ru.citeck.ecos.model.domain.docperm.dto;

import lombok.Data;
import ru.citeck.ecos.records2.RecordRef;

import java.util.List;

@Data
public class DocPermConfig {

    private String id;
    private RecordRef typeRef;
    private List<RolePermissions> permissions;
}
