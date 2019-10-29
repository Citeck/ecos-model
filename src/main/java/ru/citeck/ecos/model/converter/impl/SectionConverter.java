package ru.citeck.ecos.model.converter.impl;

import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.converter.AbstractDtoConverter;
import ru.citeck.ecos.model.domain.EcosSectionEntity;
import ru.citeck.ecos.model.domain.EcosTypeEntity;
import ru.citeck.ecos.model.dto.EcosSectionDto;
import ru.citeck.ecos.model.repository.EcosSectionRepository;
import ru.citeck.ecos.model.repository.EcosTypeRepository;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class SectionConverter extends AbstractDtoConverter<EcosSectionDto, EcosSectionEntity> {

    private final EcosTypeRepository typeRepository;
    private EcosSectionRepository sectionRepository;

    @Autowired
    public SectionConverter(EcosTypeRepository typeRepository,
                            EcosSectionRepository sectionRepository) {
        this.typeRepository = typeRepository;
        this.sectionRepository = sectionRepository;
    }

    @Override
    protected EcosSectionEntity dtoToEntity(EcosSectionDto dto) {
        EcosSectionEntity ecosSectionEntity = new EcosSectionEntity();
        ecosSectionEntity.setName(dto.getName());
        String extId = extractId(dto.getId());
        ecosSectionEntity.setExtId(extId);
        ecosSectionEntity.setDescription(dto.getDescription());
        ecosSectionEntity.setTenant(dto.getTenant());
        if (dto.getTypes() != null) {
            Set<String> dtoTypesExtIds = dto.getTypes().stream()
                .map(r -> extractId(r.getId())).collect(Collectors.toSet());
            Set<EcosTypeEntity> storedTypes = typeRepository.findAllByExtIds(dtoTypesExtIds);
            ecosSectionEntity.setTypes(storedTypes);
        }
        if (Strings.isBlank(ecosSectionEntity.getExtId())) {
            ecosSectionEntity.setExtId(UUID.randomUUID().toString());
        } else {
            Optional<EcosSectionEntity> stored = sectionRepository.findByExtId(ecosSectionEntity.getExtId());
            ecosSectionEntity.setId(stored.map(EcosSectionEntity::getId).orElse(null));
        }
        return ecosSectionEntity;
    }

    @Override
    protected EcosSectionDto entityToDto(EcosSectionEntity entity) {
        Set<RecordRef> typesRefs = null;
        if (entity.getTypes() != null) {
            typesRefs = entity.getTypes().stream()
                .map(e -> RecordRef.create("type", e.getExtId()))
                .collect(Collectors.toSet());
        }
        return new EcosSectionDto(
            entity.getExtId(),
            entity.getName(),
            entity.getDescription(),
            entity.getTenant(),
            typesRefs);
    }
}
