package ru.citeck.ecos.model.section.converter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.converter.AbstractDtoConverter;
import ru.citeck.ecos.model.lib.utils.ModelUtils;
import ru.citeck.ecos.model.section.domain.SectionEntity;
import ru.citeck.ecos.model.type.repository.TypeEntity;
import ru.citeck.ecos.model.section.dto.SectionDto;
import ru.citeck.ecos.model.section.repository.SectionRepository;
import ru.citeck.ecos.model.type.repository.TypeRepository;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Component
public class SectionConverter extends AbstractDtoConverter<SectionDto, SectionEntity> {

    private final TypeRepository typeRepository;
    private final SectionRepository sectionRepository;

    @Override
    public SectionEntity dtoToEntity(SectionDto dto) {

        SectionEntity sectionEntity = new SectionEntity();
        sectionEntity.setName(Json.getMapper().toString(dto.getName()));
        sectionEntity.setExtId(dto.getId());
        sectionEntity.setDescription(dto.getDescription());
        sectionEntity.setTenant(dto.getTenant());

        Set<String> dtoTypesExtIds;

        if (dto.getTypes() != null) {
            dtoTypesExtIds = dto.getTypes().stream()
                .map(EntityRef::getLocalId)
                .collect(Collectors.toSet());
        } else {
            dtoTypesExtIds = Collections.emptySet();
        }

        Set<TypeEntity> storedTypes = dtoTypesExtIds.isEmpty() ?
            Collections.emptySet() : typeRepository.findAllByExtIds(dtoTypesExtIds);
        sectionEntity.setTypes(storedTypes);

        ObjectData attributes = dto.getAttributes();
        if (attributes != null) {
            sectionEntity.setAttributes(attributes.toString());
        }

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
        Set<EntityRef> typesRefs = null;
        if (entity.getTypes() != null) {
            typesRefs = entity.getTypes().stream()
                .map(e -> ModelUtils.getTypeRef(e.getExtId()))
                .collect(Collectors.toSet());
        }

        ObjectData attributes = null;

        String attributesStr = entity.getAttributes();
        if (StringUtils.isNotBlank(attributesStr)) {
            try {
                attributes = Json.getMapper().read(attributesStr, ObjectData.class);
            } catch (RuntimeException ioe) {
                log.error("Cannot deserialize attributes for section entity with id: '"
                    + entity.getId() + "' Str: " + attributesStr);
            }
        }

        return new SectionDto(
            entity.getExtId(),
            Json.getMapper().read(entity.getName(), MLText.class),
            entity.getDescription(),
            entity.getTenant(),
            typesRefs,
            attributes);
    }
}
