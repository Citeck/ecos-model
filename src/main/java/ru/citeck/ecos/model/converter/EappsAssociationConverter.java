package ru.citeck.ecos.model.converter;

import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.module.ModuleRef;
import ru.citeck.ecos.apps.app.module.type.model.type.AssociationDto;
import ru.citeck.ecos.model.dao.TypeRecordsDao;
import ru.citeck.ecos.model.dto.TypeAssociationDto;
import ru.citeck.ecos.records2.RecordRef;

@Component
public class EappsAssociationConverter implements Converter<AssociationDto, TypeAssociationDto> {

    @Override
    public TypeAssociationDto sourceToTarget(AssociationDto eappsDto) {

        TypeAssociationDto localDto = new TypeAssociationDto();

        String associationId = eappsDto.getId();
        localDto.setId(associationId);

        localDto.setName(eappsDto.getName());

        ModuleRef targetTypeModuleRef = eappsDto.getTarget();
        if (targetTypeModuleRef != null) {
            localDto.setTargetType(RecordRef.create(TypeRecordsDao.ID, targetTypeModuleRef.getId()));
        } else {
            throw new IllegalArgumentException("Association with id: " + associationId
                                + "' in TypeModule have field 'targetType' with null value!");
        }

        return localDto;
    }

    @Override
    public AssociationDto targetToSource(TypeAssociationDto associationDto) {
        throw new UnsupportedOperationException("Convert local dto to eapps dto is not support!");
    }
}
