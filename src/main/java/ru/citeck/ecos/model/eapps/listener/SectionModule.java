package ru.citeck.ecos.model.eapps.listener;

import lombok.Data;
import ru.citeck.ecos.apps.module.ModuleRef;
import ru.citeck.ecos.commons.data.ObjectData;

import java.util.List;

@Data
public class SectionModule {

    private String id;
    private String name;
    private String description;
    private List<ModuleRef> types;

    private ObjectData attributes = new ObjectData();
}
