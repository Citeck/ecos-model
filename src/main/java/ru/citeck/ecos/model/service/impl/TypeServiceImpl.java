package ru.citeck.ecos.model.service.impl;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.model.converter.Converter;
import ru.citeck.ecos.model.domain.TypeEntity;
import ru.citeck.ecos.model.dto.TypeDto;
import ru.citeck.ecos.model.repository.TypeRepository;
import ru.citeck.ecos.model.service.AssociationService;
import ru.citeck.ecos.model.service.TypeService;
import ru.citeck.ecos.model.service.exception.ForgottenChildsException;
import springfox.documentation.annotations.Cacheable;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TypeServiceImpl implements TypeService {

    private final TypeRepository typeRepository;
    private final AssociationService associationService;
    private final Converter<TypeDto, TypeEntity> typeConverter;


    @Autowired
    public TypeServiceImpl(TypeRepository typeRepository,
                           AssociationService associationService,
                           Converter<TypeDto, TypeEntity> typeConverter) {
        this.typeRepository = typeRepository;
        this.associationService = associationService;
        this.typeConverter = typeConverter;
    }

    @Cacheable("types")
    public Set<TypeDto> getAll() {
        return typeRepository.findAll()
            .stream()
            .map(typeConverter::targetToSource)
            .collect(Collectors.toSet());
    }

    @Override
    public Set<TypeDto> getAll(Set<String> extIds) {
        return typeRepository.findAllByExtIds(extIds)
            .stream()
            .map(typeConverter::targetToSource)
            .collect(Collectors.toSet());
    }

    @Override
    public TypeDto getByExtId(String extId) {
        return typeRepository.findByExtId(extId).map(typeConverter::targetToSource)
            .orElseThrow(() -> new IllegalArgumentException("Type doesnt exists: " + extId));
    }

    @Override
    @Transactional
    public void delete(String extId) {
        Optional<TypeEntity> optional = typeRepository.findByExtId(extId);
        optional.ifPresent(e -> {
            if (e.getChilds().size() > 0) {
                throw new ForgottenChildsException();
            }
            typeRepository.deleteById(e.getId());
        });
    }

    @Override
    @Transactional
    public TypeDto update(TypeDto dto) {
        TypeEntity entity = typeConverter.sourceToTarget(dto);
        typeRepository.save(entity);
        if (entity.getAssocsToOther() != null) {
            associationService.saveAll(entity.getAssocsToOther());
        }
        return typeConverter.targetToSource(entity);
    }
}
