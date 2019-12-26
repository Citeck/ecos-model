package ru.citeck.ecos.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.citeck.ecos.apps.app.module.type.model.type.AssocDirection;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Objects;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class TypeAssociationDto {

    private String id;
    private String name;
    private RecordRef targetType;
    private AssocDirection direction;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypeAssociationDto that = (TypeAssociationDto) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}