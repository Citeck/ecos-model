package ru.citeck.ecos.model.eapps.listener;

import ecos.com.fasterxml.jackson210.databind.node.JsonNodeFactory;
import ecos.com.fasterxml.jackson210.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import ru.citeck.ecos.apps.module.ModuleRef;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;

@Data
public class TypeModule {

    private String id;
    private MLText name;
    private MLText description;
    private ModuleRef form;
    private ModuleRef parent;
    private boolean inheritActions;
    private String dashboardType;
    private boolean isSystem;

    private List<ModuleRef> actions = new ArrayList<>();
    private List<AssociationDto> associations = new ArrayList<>();
    private List<CreateVariantDto> createVariants = new ArrayList<>();

    private ObjectData attributes = new ObjectData();
}

