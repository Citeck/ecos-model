package ru.citeck.ecos.model.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.model.association.domain.AssociationEntity;
import ru.citeck.ecos.model.type.dto.TypeDto;
import ru.citeck.ecos.model.association.repository.AssociationRepository;
import ru.citeck.ecos.model.association.service.impl.AssociationServiceImpl;

import java.util.Collections;

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
