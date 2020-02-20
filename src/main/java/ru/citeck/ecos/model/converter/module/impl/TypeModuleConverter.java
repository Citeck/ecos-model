package ru.citeck.ecos.model.converter.module.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.module.ModuleRef;
import ru.citeck.ecos.apps.app.module.type.model.type.AssociationDto;
import ru.citeck.ecos.apps.app.module.type.model.type.CreateVariantDto;
import ru.citeck.ecos.apps.app.module.type.model.type.TypeModule;
import ru.citeck.ecos.model.converter.Converter;
import ru.citeck.ecos.model.converter.module.AbstractModuleConverter;
import ru.citeck.ecos.model.dao.TypeRecordsDao;
import ru.citeck.ecos.model.dto.TypeAssociationDto;
import ru.citeck.ecos.model.dto.TypeCreateVariantDto;
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
@RequiredArgsConstructor
@Component
public class TypeModuleConverter extends AbstractModuleConverter<TypeModule, TypeDto> {

    private final Converter<AssociationDto, TypeAssociationDto> eappsAssociationDtoConverter;
    private final Converter<CreateVariantDto, TypeCreateVariantDto> createVariantConverter;

    @Override
    public TypeDto moduleToDto(TypeModule typeModule) {

        TypeDto typeDto = new TypeDto();

        typeDto.setId(typeModule.getId());
        typeDto.setName(typeModule.getName());
        typeDto.setDescription(typeModule.getDescription());
        typeDto.setTenant(Strings.EMPTY);
        typeDto.setInheritActions(typeModule.isInheritActions());
        typeDto.setAttributes(typeModule.getAttributes());
        typeDto.setDashboardType(typeModule.getDashboardType());

        ModuleRef formModuleRef = typeModule.getForm();
        if (formModuleRef != null) {
            typeDto.setForm(formModuleRef.toString());
        }

        ModuleRef parentModuleRef = typeModule.getParent();
        if (parentModuleRef != null) {
            RecordRef parentRecordRef = RecordRef.create(TypeRecordsDao.ID, parentModuleRef.getId());
            typeDto.setParent(parentRecordRef);
        }

        List<AssociationDto> associationsModuleDTOs = typeModule.getAssociations();
        if (CollectionUtils.isNotEmpty(associationsModuleDTOs)) {
            Set<TypeAssociationDto> associationsDTOs = associationsModuleDTOs.stream()
                .map(eappsAssociationDtoConverter::sourceToTarget)
                .collect(Collectors.toSet());
            typeDto.setAssociations(associationsDTOs);
        } else {
            typeDto.setAssociations(Collections.emptySet());
        }

        List<ModuleRef> actionsList = typeModule.getActions();
        if (CollectionUtils.isNotEmpty(actionsList)) {
            Set<ModuleRef> actionsModuleRefs = new HashSet<>(actionsList);
            typeDto.setActions(actionsModuleRefs);
        } else {
            typeDto.setActions(Collections.emptySet());
        }

        List<CreateVariantDto> createVariantDtoList = typeModule.getCreateVariants();
        if (CollectionUtils.isNotEmpty(createVariantDtoList)) {
            Set<CreateVariantDto> createVariantDTOs = new HashSet<>(typeModule.getCreateVariants());
            Set<TypeCreateVariantDto> typeCreateVariantDTOs = createVariantDTOs.stream()
                .map(createVariantConverter::sourceToTarget)
                .collect(Collectors.toSet());
            typeDto.setCreateVariants(typeCreateVariantDTOs);
        }

        return typeDto;
    }

}
