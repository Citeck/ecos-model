package ru.citeck.ecos.model.association.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.model.association.domain.AssociationEntity;
import ru.citeck.ecos.model.association.repository.AssociationRepository;
import ru.citeck.ecos.model.association.service.AssociationService;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class AssociationServiceImpl implements AssociationService {

    private final AssociationRepository associationRepository;

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
