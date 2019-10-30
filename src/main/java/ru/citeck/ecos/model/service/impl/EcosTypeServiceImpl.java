package ru.citeck.ecos.model.service.impl;


import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.model.converter.Converter;
import ru.citeck.ecos.model.domain.EcosTypeEntity;
import ru.citeck.ecos.model.dto.EcosTypeDto;
import ru.citeck.ecos.model.repository.EcosAssociationRepository;
import ru.citeck.ecos.model.repository.EcosTypeRepository;
import ru.citeck.ecos.model.service.EcosTypeService;
import ru.citeck.ecos.model.service.exception.ForgottenChildsException;
import springfox.documentation.annotations.Cacheable;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EcosTypeServiceImpl implements EcosTypeService {

    private final EcosTypeRepository typeRepository;
    private final EcosAssociationRepository associationRepository;
    private final Converter<EcosTypeDto, EcosTypeEntity> converter;

    @Autowired
    public EcosTypeServiceImpl(EcosTypeRepository typeRepository,
                               EcosAssociationRepository associationRepository,
                               Converter<EcosTypeDto, EcosTypeEntity> converter) {
        this.typeRepository = typeRepository;
        this.associationRepository = associationRepository;
        this.converter = converter;
    }

    @Cacheable("types")
    public Set<EcosTypeDto> getAll() {
        return typeRepository.findAll()
            .stream()
            .map(converter::targetToSource)
            .collect(Collectors.toSet());
    }

    @Override
    public Set<EcosTypeDto> getAll(Set<String> extIds) {
        return typeRepository.findAllByExtIds(extIds)
            .stream()
            .map(converter::targetToSource)
            .collect(Collectors.toSet());
    }

    @Override
    public EcosTypeDto getByExtId(String extId) {
        return typeRepository.findByExtId(extId).map(converter::targetToSource)
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
        EcosTypeEntity entity = converter.sourceToTarget(dto);
        typeRepository.save(entity);
        if (entity.getAssocsToOther() != null) {
            saveAssociations(entity);
        }
        return converter.targetToSource(entity);
    }

    private void saveAssociations(EcosTypeEntity entity) {
        associationRepository.saveAll(entity.getAssocsToOther().stream().
            peek(assoc -> {
                if (Strings.isBlank(assoc.getExtId())) {
                    assoc.setExtId(UUID.randomUUID().toString());
                }
            }).collect(Collectors.toList()));
    }

}
