package ru.citeck.ecos.model.permissions.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.converter.DtoConverter;
import ru.citeck.ecos.model.permissions.domain.AttributesPermissionEntity;
import ru.citeck.ecos.model.permissions.dto.AttributesPermissionWithMetaDto;
import ru.citeck.ecos.model.permissions.dto.AttributesPermissionDto;
import ru.citeck.ecos.model.permissions.repository.AttributesPermissionsRepository;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.predicate.PredicateUtils;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records2.predicate.model.ValuePredicate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttributesPermissionsService {

    private final AttributesPermissionsRepository attributesPermissionsRepository;
    private final DtoConverter<AttributesPermissionWithMetaDto, AttributesPermissionEntity> attrsPermissionConverter;
    private final List<Consumer<AttributesPermissionDto>> listeners = new CopyOnWriteArrayList<>();

    public List<AttributesPermissionWithMetaDto> getAll(int max, int skip) {

        Sort sort = Sort.by(Sort.Direction.DESC, "id");
        PageRequest page = PageRequest.of(skip / max, max, sort);

        return attributesPermissionsRepository.findAll(page)
            .stream()
            .map(attrsPermissionConverter::entityToDto)
            .collect(Collectors.toList());
    }

    public List<AttributesPermissionWithMetaDto> getAll(int max, int skip, Predicate predicate, Sort sort) {

        if (sort == null) {
            sort = Sort.by(Sort.Direction.DESC, "id");
        }

        PageRequest page = PageRequest.of(skip / max, max, sort);

        return attributesPermissionsRepository.findAll(toSpec(predicate), page)
            .stream()
            .map(attrsPermissionConverter::entityToDto)
            .collect(Collectors.toList());
    }

    public Set<AttributesPermissionWithMetaDto> getAll(Collection<String> extIds) {
        return attributesPermissionsRepository.findAllByExtIds(new HashSet<>(extIds)).stream()
                .map(attrsPermissionConverter::entityToDto)
                .collect(Collectors.toSet());
    }

    public int getCount(Predicate predicate) {
        Specification<AttributesPermissionEntity> spec = toSpec(predicate);
        return spec != null ? (int) attributesPermissionsRepository.count(spec) : getCount();
    }

    public int getCount() {
        return (int) attributesPermissionsRepository.count();
    }

    public AttributesPermissionWithMetaDto save(AttributesPermissionDto dto) {

        AttributesPermissionEntity entity = toEntity(new AttributesPermissionWithMetaDto(dto));

        entity = attributesPermissionsRepository.save(entity);
        AttributesPermissionWithMetaDto changedDto = attrsPermissionConverter.entityToDto(entity);

        listeners.forEach(l -> l.accept(changedDto));

        return changedDto;

    }

    public Optional<AttributesPermissionWithMetaDto> getById(String id) {
        return Optional.ofNullable(getByIdOrNull(id));
    }

    public AttributesPermissionWithMetaDto getByIdOrNull(String id) {
        AttributesPermissionEntity entity = attributesPermissionsRepository.findByExtId(id).orElse(null);
        return entity == null ? null : attrsPermissionConverter.entityToDto(entity);
    }

    @Transactional
    public void delete(String id) {
        AttributesPermissionEntity template = attributesPermissionsRepository.findByExtId(id).orElse(null);
        if (template == null) {
            return;
        }
        attributesPermissionsRepository.delete(template);
    }

    public void addListener(Consumer<AttributesPermissionDto> listener) {
        this.listeners.add(listener);
    }

    private AttributesPermissionEntity toEntity(AttributesPermissionWithMetaDto dto) {

        AttributesPermissionEntity entity = attributesPermissionsRepository.findByExtId(dto.getId()).orElse(null);
        if (entity == null) {
            entity = attrsPermissionConverter.dtoToEntity(dto);
        }

        return entity;
    }

    private Specification<AttributesPermissionEntity> toSpec(Predicate predicate) {

        if (predicate instanceof ValuePredicate) {

            ValuePredicate valuePred = (ValuePredicate) predicate;

            ValuePredicate.Type type = valuePred.getType();
            Object value = valuePred.getValue();
            String attribute = valuePred.getAttribute();

            if (RecordConstants.ATT_MODIFIED.equals(attribute)
                    && ValuePredicate.Type.GT.equals(type)) {

                Instant instant = Json.getMapper().convert(value, Instant.class);
                if (instant != null) {
                    return (root, query, builder) ->
                            builder.greaterThan(root.get("lastModifiedDate").as(Instant.class), instant);
                }
            }
        }

        PredicateDto predicateDto = PredicateUtils.convertToDto(predicate, PredicateDto.class);
        Specification<AttributesPermissionEntity> spec = null;

        if (StringUtils.isNotBlank(predicateDto.moduleId)) {
            Specification<AttributesPermissionEntity> idSpec = (root, query, builder) ->
                builder.like(builder.lower(root.get("extId")), "%" + predicateDto.moduleId.toLowerCase() + "%");
            spec = spec != null ? spec.or(idSpec) : idSpec;
        }

        return spec;
    }

    @Data
    public static class PredicateDto {
        private String moduleId;
    }
}
