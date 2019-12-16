package ru.citeck.ecos.model.converter.impl.module;

import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.module.ModuleRef;
import ru.citeck.ecos.model.converter.AbstractModuleConverter;
import ru.citeck.ecos.model.dao.TypeRecordsDao;
import ru.citeck.ecos.model.dto.AssociationDto;
import ru.citeck.ecos.records2.RecordRef;

@Component
public class AssociationModuleDtoConverter
    extends AbstractModuleConverter<ru.citeck.ecos.apps.app.module.type.model.type.AssociationDto, AssociationDto> {

    @Override
    public AssociationDto moduleToDto(ru.citeck.ecos.apps.app.module.type.model.type.AssociationDto moduleDto) {

        AssociationDto localAssociationDto = new AssociationDto();

        String associationId = extractIdFromModuleId(moduleDto.getId());
        localAssociationDto.setId(associationId);

        localAssociationDto.setName(moduleDto.getName());

        ModuleRef targetTypeModuleRef = moduleDto.getTarget();
        if (targetTypeModuleRef != null) {
            String targetTypeId = extractIdFromModuleId(targetTypeModuleRef.getId());
            localAssociationDto.setTargetType(RecordRef.create(TypeRecordsDao.ID, targetTypeId));
        } else {
            throw new IllegalArgumentException("Association with id: " + associationId
                                + "' in TypeModule have field 'targetType' with null value!");
        }

        return localAssociationDto;
    }
}
