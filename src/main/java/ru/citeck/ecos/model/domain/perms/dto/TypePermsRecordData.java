package ru.citeck.ecos.model.domain.perms.dto;

import lombok.Value;
import ru.citeck.ecos.commons.data.entity.EntityMeta;
import ru.citeck.ecos.commons.data.entity.EntityWithMeta;
import ru.citeck.ecos.model.lib.type.dto.TypePermsDef;
import ru.citeck.ecos.model.lib.workspace.IdInWs;

/** Storage snapshot. The scope also identifies quarantined legacy rows whose type has another workspace. */
@Value
public class TypePermsRecordData {
    TypePermsDef definition;
    IdInWs id;
    EntityMeta meta;

    public IdInWs getId() {
        return id;
    }

    public EntityWithMeta<TypePermsDef> asRegistryValue() {
        return new EntityWithMeta<>(definition, meta);
    }
}
