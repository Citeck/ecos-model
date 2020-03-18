package ru.citeck.ecos.model.eapps.listener;

import lombok.Data;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.records2.RecordRef;

@Data
public class CreateVariantDto {

    private String id;
    private MLText name;
    private String formKey;
    private RecordRef formRef;
    private RecordRef recordRef;
    private ObjectData attributes = new ObjectData();
}
