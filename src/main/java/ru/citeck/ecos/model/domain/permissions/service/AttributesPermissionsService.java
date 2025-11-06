package ru.citeck.ecos.model.domain.permissions.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.context.lib.auth.AuthContext;
import ru.citeck.ecos.model.converter.DtoConverter;
import ru.citeck.ecos.model.domain.permissions.repo.AttributesPermissionEntity;
import ru.citeck.ecos.model.domain.permissions.dto.AttributesPermissionDto;
import ru.citeck.ecos.model.domain.permissions.dto.AttributesPermissionWithMetaDto;
import ru.citeck.ecos.model.domain.permissions.repo.AttributesPermissionsRepository;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy;
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverter;
import ru.citeck.ecos.webapp.lib.spring.hibernate.context.predicate.JpaSearchConverterFactory;

import jakarta.annotation.PostConstruct;
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
    private final JpaSearchConverterFactory predicateJpaService;

    private JpaSearchConverter<AttributesPermissionEntity> searchConv;

    @PostConstruct
    public void init() {
        searchConv = predicateJpaService.createConverter(AttributesPermissionEntity.class).build();
    }

    public List<AttributesPermissionWithMetaDto> getAll(int max, int skip) {

        Sort sort = Sort.by(Sort.Direction.DESC, "id");
        PageRequest page = PageRequest.of(skip / max, max, sort);

        return attributesPermissionsRepository.findAll(page)
            .stream()
            .map(attrsPermissionConverter::entityToDto)
            .collect(Collectors.toList());
    }

    public List<AttributesPermissionWithMetaDto> getAll(int max, int skip, Predicate predicate, List<SortBy> sort) {

        return searchConv.findAll(attributesPermissionsRepository, predicate, max, skip, sort)
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
        return (int) searchConv.getCount(attributesPermissionsRepository, predicate);
    }

    public int getCount() {
        return (int) attributesPermissionsRepository.count();
    }

    public AttributesPermissionWithMetaDto save(AttributesPermissionDto dto) {
        if (!AuthContext.isRunAsSystemOrAdmin()) {
            throw new SecurityException("Permission denied. You can't modify permissions");
        }

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
        if (!AuthContext.isRunAsSystemOrAdmin()) {
            throw new SecurityException("Permission denied. You can't modify permissions");
        }

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

    @Data
    public static class PredicateDto {
        private String moduleId;
    }
}
