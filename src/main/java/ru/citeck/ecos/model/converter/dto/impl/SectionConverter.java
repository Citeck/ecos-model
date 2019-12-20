package ru.citeck.ecos.model.converter.dto.impl;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.converter.dto.AbstractDtoConverter;
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

@RequiredArgsConstructor
@Component
public class SectionConverter extends AbstractDtoConverter<SectionDto, SectionEntity> {

    private final TypeRepository typeRepository;
    private final SectionRepository sectionRepository;

    @Override
    public SectionEntity dtoToEntity(SectionDto dto) {

        SectionEntity sectionEntity = new SectionEntity();
        sectionEntity.setName(dto.getName());
        sectionEntity.setExtId(dto.getId());
        sectionEntity.setDescription(dto.getDescription());
        sectionEntity.setTenant(dto.getTenant());

        Set<String> dtoTypesExtIds = dto.getTypes().stream()
            .map(RecordRef::getId)
            .collect(Collectors.toSet());
        Set<TypeEntity> storedTypes = typeRepository.findAllByExtIds(dtoTypesExtIds);
        sectionEntity.setTypes(storedTypes);

        String sectionDtoId = dto.getId();
        if (Strings.isBlank(sectionDtoId)) {
            sectionEntity.setExtId(UUID.randomUUID().toString());
        } else {
            sectionEntity.setExtId(sectionDtoId);
        }

        Optional<SectionEntity> storedSection = sectionRepository.findByExtId(sectionEntity.getExtId());
        storedSection.ifPresent(s -> sectionEntity.setId(s.getId()));

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
