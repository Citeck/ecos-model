package ru.citeck.ecos.model.service.impl;


import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.model.domain.EcosTypeEntity;
import ru.citeck.ecos.model.dto.EcosTypeDto;
import ru.citeck.ecos.model.repository.EcosTypeRepository;
import ru.citeck.ecos.model.service.EcosTypeService;
import ru.citeck.ecos.model.service.exception.ForgottenChildsException;
import ru.citeck.ecos.model.service.converter.EcosTypeConverter;
import springfox.documentation.annotations.Cacheable;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EcosTypeServiceImpl implements EcosTypeService {

    private final EcosTypeRepository typeRepository;
    private final EcosTypeConverter ecosTypeConverter;

    @Autowired
    public EcosTypeServiceImpl(EcosTypeRepository typeRepository, EcosTypeConverter ecosTypeConverter) {
        this.typeRepository = typeRepository;
        this.ecosTypeConverter = ecosTypeConverter;
    }

    @Cacheable("types")
    public Set<EcosTypeDto> getAll() {
        return typeRepository.findAll().stream()
            .map(ecosTypeConverter::entityToDto)
            .collect(Collectors.toSet());
    }

    @Override
    public Set<EcosTypeDto> getAll(Set<String> extIds) {
        return typeRepository.findAllByExtIds(extIds).stream()
            .map(ecosTypeConverter::entityToDto)
            .collect(Collectors.toSet());
    }

    @Override
    public EcosTypeDto getByExtId(String extId) {
        return typeRepository.findByExtId(extId).map(ecosTypeConverter::entityToDto)
            .orElseThrow(() -> new IllegalArgumentException("Type doesnt exists: " + extId));
    }

    @Override
    @Transactional
    public void delete(String extId) {
        Optional<EcosTypeEntity> optional = typeRepository.findByExtId(extId);
        optional.ifPresent(e -> {
            if (e.getChilds() != null) {
                throw new ForgottenChildsException();
            }
            typeRepository.deleteById(e.getId());
        });
    }

    @Override
    @Transactional
    public EcosTypeDto update(EcosTypeDto dto) {
        EcosTypeEntity entity = ecosTypeConverter.dtoToEntity(dto);
        if (Strings.isBlank(entity.getExtId())) {
            entity.setExtId(UUID.randomUUID().toString());
        } else {
            Optional<EcosTypeEntity> stored = typeRepository.findByExtId(entity.getExtId());
            entity.setId(stored.map(EcosTypeEntity::getId).orElse(null));
        }
        EcosTypeEntity saved = typeRepository.save(entity);
        return ecosTypeConverter.entityToDto(saved);
    }

}
