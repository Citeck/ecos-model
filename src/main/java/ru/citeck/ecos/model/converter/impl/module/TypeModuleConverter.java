package ru.citeck.ecos.model.converter.impl.module;

import org.apache.commons.collections.CollectionUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.module.ModuleRef;
import ru.citeck.ecos.apps.app.module.type.model.type.TypeModule;
import ru.citeck.ecos.model.converter.AbstractModuleConverter;
import ru.citeck.ecos.model.converter.ModuleConverter;
import ru.citeck.ecos.model.dao.TypeRecordsDao;
import ru.citeck.ecos.model.dto.AssociationDto;
import ru.citeck.ecos.model.dto.TypeDto;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converting type module objects to local dto objects.
 *
 * @see ru.citeck.ecos.apps.app.module.type.model.type.TypeModule
 * @see ru.citeck.ecos.model.dto.TypeDto
 */
@Component
public class TypeModuleConverter extends AbstractModuleConverter<TypeModule, TypeDto> {

    private final ModuleConverter<ru.citeck.ecos.apps.app.module.type.model.type.AssociationDto, AssociationDto> associationDtoModuleConverter;

    public TypeModuleConverter(AssociationModuleDtoConverter associationDtoModuleConverter) {
        this.associationDtoModuleConverter = associationDtoModuleConverter;
    }

    @Override
    public TypeDto moduleToDto(TypeModule typeModule) {

        TypeDto typeDto = new TypeDto();

        String typeId = extractIdFromModuleId(typeModule.getId());
        typeDto.setId(typeId);

        typeDto.setName(typeModule.getName());
        typeDto.setDescription(typeModule.getDescription());
        typeDto.setTenant(Strings.EMPTY);
        typeDto.setInheritActions(typeModule.isInheritActions());

        ModuleRef parentModuleRef = typeModule.getParent();
        if (parentModuleRef != null) {
            String parentModuleId = parentModuleRef.getId();
            String parentId = extractIdFromModuleId(parentModuleId);
            RecordRef parentRecordRef = RecordRef.create(TypeRecordsDao.ID, parentId);
            typeDto.setParent(parentRecordRef);
        }

        ModuleRef formModuleRef = typeModule.getForm();
        if (formModuleRef != null) {
            typeDto.setForm(formModuleRef.toString());
        }

        List<ru.citeck.ecos.apps.app.module.type.model.type.AssociationDto> associationsModuleDTOs =
            typeModule.getAssociations();
        if (CollectionUtils.isNotEmpty(associationsModuleDTOs)) {
            Set<AssociationDto> associationsDTOs = associationsModuleDTOs.stream()
                .map(associationDtoModuleConverter::moduleToDto)
                .collect(Collectors.toSet());
            typeDto.setAssociations(associationsDTOs);
        } else {
            typeDto.setAssociations(Collections.emptySet());
        }

        List<ModuleRef> actionsModuleRefs = typeModule.getActions();
        if (CollectionUtils.isNotEmpty(actionsModuleRefs)) {
            typeDto.setActions(new HashSet<>(actionsModuleRefs));
        } else {
            typeDto.setActions(Collections.emptySet());
        }

        return typeDto;
    }

}
