package ru.citeck.ecos.model.service.impl;


import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.model.converter.Converter;
import ru.citeck.ecos.model.domain.TypeEntity;
import ru.citeck.ecos.model.dto.TypeDto;
import ru.citeck.ecos.model.repository.AssociationRepository;
import ru.citeck.ecos.model.repository.TypeRepository;
import ru.citeck.ecos.model.service.TypeService;
import ru.citeck.ecos.model.service.exception.ForgottenChildsException;
import springfox.documentation.annotations.Cacheable;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TypeServiceImpl implements TypeService {

    private final TypeRepository typeRepository;
    private final AssociationRepository associationRepository;
    private final Converter<TypeDto, TypeEntity> converter;

    @Autowired
    public TypeServiceImpl(TypeRepository typeRepository,
                           AssociationRepository associationRepository,
                           Converter<TypeDto, TypeEntity> converter) {
        this.typeRepository = typeRepository;
        this.associationRepository = associationRepository;
        this.converter = converter;
    }

    @Cacheable("types")
    public Set<TypeDto> getAll() {
        return typeRepository.findAll()
            .stream()
            .map(converter::targetToSource)
            .collect(Collectors.toSet());
    }

    @Override
    public Set<TypeDto> getAll(Set<String> extIds) {
        return typeRepository.findAllByExtIds(extIds)
            .stream()
            .map(converter::targetToSource)
            .collect(Collectors.toSet());
    }

    @Override
    public TypeDto getByExtId(String extId) {
        return typeRepository.findByExtId(extId).map(converter::targetToSource)
            .orElseThrow(() -> new IllegalArgumentException("Type doesnt exists: " + extId));
    }

    @Override
    @Transactional
    public void delete(String extId) {
        Optional<TypeEntity> optional = typeRepository.findByExtId(extId);
        optional.ifPresent(e -> {
            if (e.getChilds() != null) {
                throw new ForgottenChildsException();
            }
            typeRepository.deleteById(e.getId());
        });
    }

    @Override
    @Transactional
    public TypeDto update(TypeDto dto) {
        TypeEntity entity = converter.sourceToTarget(dto);
        typeRepository.save(entity);
        if (entity.getAssocsToOther() != null) {
            saveAssociations(entity);
        }
        return converter.targetToSource(entity);
    }

    private void saveAssociations(TypeEntity entity) {
        associationRepository.saveAll(entity.getAssocsToOther().stream().
            peek(assoc -> {
                if (Strings.isBlank(assoc.getExtId())) {
                    assoc.setExtId(UUID.randomUUID().toString());
                }
            }).collect(Collectors.toList()));
    }

}
