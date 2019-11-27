package ru.citeck.ecos.model.service.impl;

import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.model.domain.AssociationEntity;
import ru.citeck.ecos.model.repository.AssociationRepository;
import ru.citeck.ecos.model.service.AssociationService;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AssociationServiceImpl implements AssociationService {

    private final AssociationRepository associationRepository;

    @Autowired
    public AssociationServiceImpl(AssociationRepository associationRepository) {
        this.associationRepository = associationRepository;
    }

    @Override
    public void saveAll(Set<AssociationEntity> associationEntities) {
        associationRepository.saveAll(associationEntities.stream().
            peek(assoc -> {
                if (Strings.isBlank(assoc.getExtId())) {
                    assoc.setExtId(UUID.randomUUID().toString());
                }
            }).collect(Collectors.toList()));
    }
}
