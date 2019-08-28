package ru.citeck.ecos.model.service.impl;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.model.domain.EcosTypeEntity;
import ru.citeck.ecos.model.dto.EcosTypeDto;
import ru.citeck.ecos.model.repository.EcosTypeRepository;
import ru.citeck.ecos.model.service.EcosTypeService;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class EcosTypeServiceImpl implements EcosTypeService {

    private final EcosTypeRepository typeRepository;

    @Autowired
    public EcosTypeServiceImpl(EcosTypeRepository typeRepository) {
        this.typeRepository = typeRepository;
    }

    public List<EcosTypeDto> getAll() {
        return typeRepository.findAll().stream()
            .map(this::entityToDto)
            .collect(Collectors.toList());
    }

    @Override
    public List<EcosTypeDto> getAll(List<Long> ids) {
        return typeRepository.findAllById(ids).stream()
            .map(this::entityToDto)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<EcosTypeDto> getById(Long id) {
        return typeRepository.findById(id).map(this::entityToDto);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Optional<EcosTypeEntity> optional = typeRepository.findById(id);
        optional.ifPresent(e -> typeRepository.deleteById(e.getId()));
    }

    @Override
    @Transactional
    public void update(EcosTypeDto dto) {
        Optional<EcosTypeEntity> optional = typeRepository.findById(dto.getId());
        EcosTypeEntity entity = optional.orElseGet(EcosTypeEntity::new);
        EcosTypeDto localDto = entityToDto(entity);

        if (!Objects.equals(localDto, dto)) {
            entity = dtoToEntity(dto);
            typeRepository.save(entity);
        }
    }

    private EcosTypeDto entityToDto(EcosTypeEntity entity) {
        return new EcosTypeDto(
            entity.getId(),
            entity.getDecription(),
            entity.getName(),
            entity.getTenant());
    }

    private EcosTypeEntity dtoToEntity(EcosTypeDto dto) {
        return new EcosTypeEntity(
            dto.getId(),
            dto.getName(),
            dto.getDecription(),
            dto.getTenant());
    }

}
