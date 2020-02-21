package ru.citeck.ecos.model.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.apps.app.module.ModuleRef;
import ru.citeck.ecos.apps.app.module.type.model.type.TypeModule;
import ru.citeck.ecos.model.converter.dto.DtoConverter;
import ru.citeck.ecos.model.converter.module.impl.TypeModuleConverter;
import ru.citeck.ecos.model.domain.TypeEntity;
import ru.citeck.ecos.model.dto.TypeDto;
import ru.citeck.ecos.model.repository.TypeRepository;
import ru.citeck.ecos.model.service.AssociationService;
import ru.citeck.ecos.model.service.TypeService;
import ru.citeck.ecos.model.service.exception.ForgottenChildsException;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.objdata.ObjectData;
import ru.citeck.ecos.records2.scalar.MLText;
import ru.citeck.ecos.records2.utils.json.JsonUtils;
import springfox.documentation.annotations.Cacheable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class TypeServiceImpl implements TypeService {

    private final TypeRepository typeRepository;
    private final AssociationService associationService;
    private final DtoConverter<TypeDto, TypeEntity> typeConverter;
    private final TypeModuleConverter moduleConverter;

    private final RecordsService recordsService;

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

            TypeModule newType = new TypeModule();
            newType.setParent(ModuleRef.create("model/type", "user-base"));
            newType.setInheritActions(true);
            newType.setName(new MLText(extId));

            ObjectData data = JsonUtils.convert(newType, ObjectData.class);
            data.set("module_id", extId);

            RecordMeta meta = new RecordMeta();
            meta.setAttributes(data);

            recordsService.mutate(meta);

            return moduleConverter.moduleToDto(newType);
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
        typeRepository.save(entity);
        associationService.extractAndSaveAssocsFromType(dto);
        return typeConverter.entityToDto(entity);
    }
}
