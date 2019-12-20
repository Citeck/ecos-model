package ru.citeck.ecos.model.service.impl;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.model.converter.dto.DtoConverter;
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

@RequiredArgsConstructor
@Service
public class TypeServiceImpl implements TypeService {

    private final TypeRepository typeRepository;
    private final AssociationService associationService;
    private final DtoConverter<TypeDto, TypeEntity> typeConverter;

    @Cacheable("types")
    public Set<TypeDto> getAll() {
        return typeRepository.findAll().stream()
            .map(typeConverter::entityToDto)
            .collect(Collectors.toSet());
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
        return typeConverter.entityToDto(entity);
    }
}
