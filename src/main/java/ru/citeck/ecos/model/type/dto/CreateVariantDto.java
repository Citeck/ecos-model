package ru.citeck.ecos.model.type.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.records2.RecordRef;

@Data
@NoArgsConstructor
public class CreateVariantDto {

    private String id;
    @NotNull
    private MLText name;
    private RecordRef formRef;
    private RecordRef recordRef;
    @NotNull
    private ObjectData attributes = ObjectData.create();

    public CreateVariantDto(CreateVariantDto other) {
        CreateVariantDto copy = Json.getMapper().copy(other);
        if (copy == null) {
            return;
        }
        this.id = copy.id;
        this.name = copy.name;
        this.formRef = copy.formRef;
        this.recordRef = copy.recordRef;
        this.attributes = copy.attributes;
    }
}
