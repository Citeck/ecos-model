package ru.citeck.ecos.model.service;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.model.converter.DtoConverter;
import ru.citeck.ecos.model.association.domain.AssociationEntity;
import ru.citeck.ecos.model.type.domain.TypeEntity;
import ru.citeck.ecos.model.type.dto.TypeDto;
import ru.citeck.ecos.model.association.dto.AssociationDto;
import ru.citeck.ecos.model.association.repository.AssociationRepository;
import ru.citeck.ecos.model.type.dto.TypeWithMetaDto;
import ru.citeck.ecos.model.type.repository.TypeRepository;
import ru.citeck.ecos.model.association.service.impl.AssociationServiceImpl;
import ru.citeck.ecos.model.type.records.dao.TypeRecordsDao;
import ru.citeck.ecos.records2.RecordRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
public class AssociationServiceImplTest {

    @MockBean
    private AssociationRepository associationRepository;

    private AssociationServiceImpl associationService;

    private AssociationEntity associationEntity;

    @BeforeEach
    void setUp() {
        associationService = new AssociationServiceImpl(associationRepository);
        associationEntity = new AssociationEntity();
    }

    @Test
    void testSaveAll() {

        //  arrange
        when(associationRepository.saveAll(Collections.singleton(associationEntity)))
            .thenReturn(Collections.singletonList(associationEntity));

        //  act
        associationService.saveAll(Collections.singleton(associationEntity));

        //  assert
        Mockito.verify(associationRepository,Mockito.times(1))
            .saveAll(Collections.singletonList(associationEntity));

    }
}
