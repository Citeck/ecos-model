package ru.citeck.ecos.model.service.converter;

import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.module.type.type.action.ActionDto;
import ru.citeck.ecos.model.domain.EcosAssociationEntity;
import ru.citeck.ecos.model.domain.EcosTypeEntity;
import ru.citeck.ecos.model.dto.EcosTypeDto;
import ru.citeck.ecos.model.repository.EcosAssociationRepository;
import ru.citeck.ecos.model.repository.EcosTypeRepository;
import ru.citeck.ecos.model.service.exception.ParentNotFoundException;
import ru.citeck.ecos.records2.RecordRef;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class EcosTypeConverter {

    private final EcosTypeRepository typeRepository;
    private final EcosAssociationRepository associationRepository;

    public EcosTypeConverter(EcosTypeRepository typeRepository, EcosAssociationRepository associationRepository) {
        this.typeRepository = typeRepository;
        this.associationRepository = associationRepository;
    }

    public EcosTypeDto entityToDto(EcosTypeEntity entity) {
        RecordRef parent = null;
        if (entity.getParent() != null) {
            parent = RecordRef.create("type", entity.getParent().getExtId());
        }
        Set<RecordRef> associationsRefs = null;
        if (entity.getAssocsToOther() != null) {
            associationsRefs = entity.getAssocsToOther().stream()
                .map(assoc -> RecordRef.create("association", assoc.getExtId()))
                .collect(Collectors.toSet());
        }

        List<ActionDto> actions = entity.getActions()
            .stream()
            .map(ActionConverter::toDto)
            .collect(Collectors.toList());

        return new EcosTypeDto(
            entity.getExtId(),
            entity.getName(),
            entity.getDescription(),
            entity.getTenant(),
            parent,
            associationsRefs,
            actions,
            entity.isInheritActions());
    }

    public EcosTypeEntity dtoToEntity(EcosTypeDto dto) {
        EcosTypeEntity ecosTypeEntity = new EcosTypeEntity();
        ecosTypeEntity.setName(dto.getName());
        ecosTypeEntity.setExtId(dto.getId());
        ecosTypeEntity.setDescription(dto.getDescription());
        ecosTypeEntity.setTenant(dto.getTenant());
        ecosTypeEntity.setInheritActions(dto.isInheritActions());

        EcosTypeEntity parent = null;
        if (dto.getParent() != null && Strings.isNotBlank(dto.getParent().getId())) {
            parent = typeRepository.findByExtId(dto.getParent().getId())
                .orElseThrow(() -> new ParentNotFoundException(dto.getParent().getId()));
        }
        ecosTypeEntity.setParent(parent);

        Set<RecordRef> associationsRefs = dto.getAssociations();
        Set<EcosAssociationEntity> associationEntities = null;
        if (associationsRefs != null && associationsRefs.size() != 0) {
            associationEntities = associationsRefs.stream()
                .map(assoc -> associationRepository.findByExtId(assoc.getId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
        }
        ecosTypeEntity.setAssocsToOther(associationEntities);

        dto.getActions()
            .stream()
            .map(ActionConverter::fromDto)
            .forEach(ecosTypeEntity::addAction);

        return ecosTypeEntity;
    }


}
