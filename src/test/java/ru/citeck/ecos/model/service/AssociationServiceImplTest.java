package ru.citeck.ecos.model.service;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.model.converter.dto.DtoConverter;
import ru.citeck.ecos.model.dao.TypeRecordsDao;
import ru.citeck.ecos.model.domain.AssociationEntity;
import ru.citeck.ecos.model.domain.TypeEntity;
import ru.citeck.ecos.model.dto.TypeAssociationDto;
import ru.citeck.ecos.model.dto.TypeDto;
import ru.citeck.ecos.model.repository.AssociationRepository;
import ru.citeck.ecos.model.repository.TypeRepository;
import ru.citeck.ecos.model.service.impl.AssociationServiceImpl;
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

    @MockBean
    private TypeRepository typeRepository;

    @MockBean
    private DtoConverter<TypeAssociationDto, AssociationEntity> associationConverter;

    @MockBean
    private DtoConverter<TypeDto, TypeEntity> typeConverter;

    private AssociationServiceImpl associationService;

    private AssociationEntity associationEntity;

    @BeforeEach
    void setUp() {
        associationService = new AssociationServiceImpl(associationRepository, typeRepository, associationConverter,
            typeConverter);
        associationEntity = new AssociationEntity();
    }

    @Test
    void testExtractAndSaveAssocsFromType() {

        //  arrange
        TypeAssociationDto associationDto = new TypeAssociationDto();
        associationDto.setId("assocId");
        associationDto.setTargetType(RecordRef.create(TypeRecordsDao.ID, "typeId"));

        AssociationEntity associationEntity = new AssociationEntity();
        associationEntity.setExtId("assocId");

        when(associationConverter.dtoToEntity(associationDto)).thenReturn(associationEntity);

        TypeDto typeDto = new TypeDto();
        typeDto.setAssociations(Collections.singleton(associationDto));

        TypeEntity typeEntity = new TypeEntity();
        typeEntity.setId(1L);
        typeEntity.setExtId("typeId");

        when(typeConverter.dtoToEntity(typeDto)).thenReturn(typeEntity);
        when(typeRepository.findByExtId("typeId")).thenReturn(Optional.of(typeEntity));

        //  act
        associationService.extractAndSaveAssocsFromType(typeDto);

        //  assert
        List<AssociationEntity> associationEntities = new ArrayList<>();
        associationEntities.add(associationEntity);
        Mockito.verify(associationRepository, times(1))
            .saveAll(associationEntities);
    }

    @Test
    void testExtractAndSaveAssocsFromTypeTargetTypeIsNull() {

        //  arrange
        TypeAssociationDto associationDto = new TypeAssociationDto();
        associationDto.setId("assocId");

        AssociationEntity associationEntity = new AssociationEntity();
        associationEntity.setExtId("assocId");

        when(associationConverter.dtoToEntity(associationDto)).thenReturn(associationEntity);

        TypeDto typeDto = new TypeDto();
        typeDto.setAssociations(Collections.singleton(associationDto));

        TypeEntity typeEntity = new TypeEntity();
        typeEntity.setId(1L);
        typeEntity.setExtId("typeId");

        when(typeConverter.dtoToEntity(typeDto)).thenReturn(typeEntity);
        when(typeRepository.findByExtId("typeId")).thenReturn(Optional.of(typeEntity));

        //  act
        try {
            associationService.extractAndSaveAssocsFromType(typeDto);
        } catch (IllegalArgumentException iae) {
            //  assert
            Assert.assertEquals("Target type is null", iae.getMessage());
            Mockito.verify(associationRepository, times(0))
                .saveAll(Mockito.anySet());
        }
    }

    @Test
    void testExtractAndSaveAssocsFromTypeTargetTypeIsNotExists() {

        //  arrange
        TypeAssociationDto associationDto = new TypeAssociationDto();
        associationDto.setId("assocId");
        associationDto.setTargetType(RecordRef.create(TypeRecordsDao.ID, "notExistableTypeId"));

        AssociationEntity associationEntity = new AssociationEntity();
        associationEntity.setExtId("assocId");

        when(associationConverter.dtoToEntity(associationDto)).thenReturn(associationEntity);

        TypeDto typeDto = new TypeDto();
        typeDto.setAssociations(Collections.singleton(associationDto));

        TypeEntity typeEntity = new TypeEntity();
        typeEntity.setId(1L);
        typeEntity.setExtId("typeId");

        when(typeConverter.dtoToEntity(typeDto)).thenReturn(typeEntity);
        when(typeRepository.findByExtId("typeId")).thenReturn(Optional.of(typeEntity));

        //  act
        try {
            associationService.extractAndSaveAssocsFromType(typeDto);
        } catch (IllegalArgumentException iae) {
            //  assert
            Assert.assertEquals("Target type doesnt exists: notExistableTypeId", iae.getMessage());
            Mockito.verify(associationRepository, times(0))
                .saveAll(Mockito.anySet());
        }
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
