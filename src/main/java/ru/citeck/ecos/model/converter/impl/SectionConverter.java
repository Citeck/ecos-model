package ru.citeck.ecos.model.converter.impl;

import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.converter.AbstractDtoConverter;
import ru.citeck.ecos.model.dao.TypeRecordsDao;
import ru.citeck.ecos.model.domain.SectionEntity;
import ru.citeck.ecos.model.domain.TypeEntity;
import ru.citeck.ecos.model.dto.SectionDto;
import ru.citeck.ecos.model.repository.SectionRepository;
import ru.citeck.ecos.model.repository.TypeRepository;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class SectionConverter extends AbstractDtoConverter<SectionDto, SectionEntity> {

    private final TypeRepository typeRepository;
    private final SectionRepository sectionRepository;

    @Autowired
    public SectionConverter(TypeRepository typeRepository,
                            SectionRepository sectionRepository) {
        this.typeRepository = typeRepository;
        this.sectionRepository = sectionRepository;
    }

    @Override
    public SectionEntity dtoToEntity(SectionDto dto) {

        String extId = extractId(dto.getId());

        SectionEntity sectionEntity = new SectionEntity();
        sectionEntity.setName(dto.getName());
        sectionEntity.setExtId(extId);
        sectionEntity.setDescription(dto.getDescription());
        sectionEntity.setTenant(dto.getTenant());

        if (dto.getTypes() != null) {
            Set<String> dtoTypesExtIds = dto.getTypes().stream()
                .map(r -> extractId(r.getId())).collect(Collectors.toSet());
            Set<TypeEntity> storedTypes = typeRepository.findAllByExtIds(dtoTypesExtIds);
            sectionEntity.setTypes(storedTypes);
        }

        if (Strings.isBlank(sectionEntity.getExtId())) {
            sectionEntity.setExtId(UUID.randomUUID().toString());
        } else {
            Optional<SectionEntity> stored = sectionRepository.findByExtId(sectionEntity.getExtId());
            sectionEntity.setId(stored.map(SectionEntity::getId).orElse(null));
        }

        return sectionEntity;
    }

    @Override
    public SectionDto entityToDto(SectionEntity entity) {
        Set<RecordRef> typesRefs = null;
        if (entity.getTypes() != null) {
            typesRefs = entity.getTypes().stream()
                .map(e -> RecordRef.create(TypeRecordsDao.ID, e.getExtId()))
                .collect(Collectors.toSet());
        }
        return new SectionDto(
            entity.getExtId(),
            entity.getName(),
            entity.getDescription(),
            entity.getTenant(),
            typesRefs);
    }
}
