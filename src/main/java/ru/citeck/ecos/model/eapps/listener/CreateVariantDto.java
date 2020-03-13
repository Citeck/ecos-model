package ru.citeck.ecos.model.eapps.listener;

import lombok.Data;
import ru.citeck.ecos.apps.module.ModuleRef;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.records2.RecordRef;

@Data
public class CreateVariantDto {

    private String id;
    private String name;
    private ModuleRef formRef;
    private String formKey;
    private RecordRef recordRef;
    private ObjectData attributes = new ObjectData();
}
