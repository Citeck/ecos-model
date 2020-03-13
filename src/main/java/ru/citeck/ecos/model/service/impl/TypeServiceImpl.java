package ru.citeck.ecos.model.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.model.converter.dto.DtoConverter;
import ru.citeck.ecos.model.domain.TypeEntity;
import ru.citeck.ecos.model.dto.TypeDto;
import ru.citeck.ecos.model.repository.TypeRepository;
import ru.citeck.ecos.model.service.AssociationService;
import ru.citeck.ecos.model.service.TypeService;
import ru.citeck.ecos.model.service.exception.ForgottenChildsException;
import ru.citeck.ecos.records2.RecordRef;
import springfox.documentation.annotations.Cacheable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class TypeServiceImpl implements TypeService {

    private final TypeRepository typeRepository;
    private final AssociationService associationService;
    private final DtoConverter<TypeDto, TypeEntity> typeConverter;

    private Consumer<TypeDto> onTypeChangedListener;

    @Override
    public List<TypeDto> getAll(int max, int skip) {

        PageRequest page = PageRequest.of(skip / max, max, Sort.by(Sort.Direction.DESC, "id"));

        return typeRepository.findAll(page)
            .stream()
            .map(typeConverter::targetToSource)
            .collect(Collectors.toList());
    }

    @Override
    public int getCount() {
        return (int) typeRepository.count();
    }

    @Override
    public void addListener(Consumer<TypeDto> onTypeChangedListener) {
        this.onTypeChangedListener = onTypeChangedListener;
    }

    @Cacheable("types")
    public Set<TypeDto> getAll() {
        return typeRepository.findAll().stream()
            .map(typeConverter::entityToDto)
            .collect(Collectors.toSet());
    }

    @Override
    public String getDashboardType(String extId) {

        AtomicReference<String> result = new AtomicReference<>();

        forEachTypeInHierarchy(extId, type -> {
            if (StringUtils.isNotBlank(type.getDashboardType())) {
                result.set(type.getDashboardType());
                return true;
            }
            return false;
        });

        return result.get();
    }

    @Override
    public List<TypeDto> getParents(String extId) {

        List<TypeDto> result = new ArrayList<>();
        forEachTypeInHierarchy(extId, type -> {
            result.add(type);
            return false;
        });

        return result;
    }

    private void forEachTypeInHierarchy(String extId, Function<TypeDto, Boolean> action) {

        TypeDto type = getByExtId(extId);
        if (action.apply(type)) {
            return;
        }

        while (type != null) {

            RecordRef parentRef = type.getParent();

            if (parentRef != null) {
                type = getByExtId(parentRef.getId());
                if (type != null) {
                    if (action.apply(type)) {
                        return;
                    }
                }
            } else {
                type = null;
            }
        }
    }

    @Override
    public Set<TypeDto> getAll(Set<String> extIds) {
        return typeRepository.findAllByExtIds(extIds).stream()
            .map(typeConverter::entityToDto)
            .collect(Collectors.toSet());
    }

    @Override
    public TypeDto getByExtId(String extId) {
        return typeRepository.findByExtId(extId).map(typeConverter::entityToDto)
            .orElseThrow(() -> new IllegalArgumentException("Type doesnt exists: " + extId));
    }

    @Override
    public TypeDto getOrCreateByExtId(String extId) {

        Optional<TypeEntity> byExtId = typeRepository.findByExtId(extId);

        return byExtId.map(typeConverter::entityToDto)
            .orElseGet(() -> {

            if ("base".equals(extId) || "user-base".equals(extId)) {
                throw new IllegalStateException("Base type doesn't exists!");
            }

            TypeDto typeDto = new TypeDto();
            typeDto.setId(extId);
            typeDto.setInheritActions(true);
            typeDto.setParent(RecordRef.create("emodel", "type", "user-base"));
            typeDto.setName(new MLText(extId));

            return save(typeDto);
        });
    }

    public TypeDto getBaseType() {
        return typeRepository.findByExtId("base")
            .map(typeConverter::entityToDto)
            .orElseThrow(() -> new IllegalArgumentException("Base type doesn't exists"));
    }

    @Override
    @Transactional
    public void delete(String extId) {
        Optional<TypeEntity> optional = typeRepository.findByExtId(extId);
        optional.ifPresent(e -> {
            if (e.getChildren().size() > 0) {
                throw new ForgottenChildsException();
            }
            typeRepository.deleteById(e.getId());
        });
    }

    @Override
    @Transactional
    public TypeDto save(TypeDto dto) {
        TypeEntity entity = typeConverter.dtoToEntity(dto);
        entity = typeRepository.save(entity);
        associationService.extractAndSaveAssocsFromType(dto);
        TypeDto typeDto = typeConverter.entityToDto(entity);
        onTypeChangedListener.accept(typeDto);
        return typeDto;
    }
}
