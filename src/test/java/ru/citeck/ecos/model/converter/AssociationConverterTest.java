package ru.citeck.ecos.model.converter;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.model.converter.dto.impl.TypeAssociationConverter;
import ru.citeck.ecos.model.domain.AssociationEntity;
import ru.citeck.ecos.model.domain.TypeEntity;
import ru.citeck.ecos.model.dto.TypeAssociationDto;
import ru.citeck.ecos.model.dto.TypeDto;
import ru.citeck.ecos.model.repository.AssociationRepository;
import ru.citeck.ecos.records2.RecordRef;

@ExtendWith(SpringExtension.class)
public class AssociationConverterTest {

    @MockBean
    private AssociationRepository associationRepository;

    private TypeAssociationConverter associationConverter;

    private TypeAssociationDto associationDto;
    private AssociationEntity associationEntity;
    private TypeEntity sourceTypeEntity;
    private TypeEntity targetTypeEntity;

    @BeforeEach
    void setUp() {
        associationConverter = new TypeAssociationConverter();

        associationDto = new TypeAssociationDto();
        associationDto.setId("association");
        associationDto.setName("name");
        associationDto.setTargetType(RecordRef.create("type", "target"));

        sourceTypeEntity = new TypeEntity();
        sourceTypeEntity.setExtId("source");

        TypeDto sourceTypeDto = new TypeDto();
        sourceTypeDto.setId("source");

        targetTypeEntity = new TypeEntity();
        targetTypeEntity.setExtId("target");

        TypeDto targetTypeDto = new TypeDto();
        targetTypeDto.setId("target");

        associationEntity = new AssociationEntity();
        associationEntity.setExtId("association");
        associationEntity.setName("name");
        associationEntity.setTarget(targetTypeEntity);
    }

    @Test
    void testDtoToEntity() {

        //  arrange
        //when(typeRepository.findByExtId("source")).thenReturn(Optional.of(sourceTypeEntity));
        //when(typeRepository.findByExtId("target")).thenReturn(Optional.of(targetTypeEntity));

        //  act
        AssociationEntity resultAssociationEntity = associationConverter.dtoToEntity(associationDto);

        //  assert
        Assert.assertEquals(resultAssociationEntity.getExtId(), associationDto.getId());
        Assert.assertEquals(resultAssociationEntity.getName(), associationDto.getName());
        //Assert.assertEquals(resultAssociationEntity.getSource(), sourceTypeEntity);
        //Assert.assertEquals(resultAssociationEntity.getTarget(), targetTypeEntity);
    }

    @Test
    void testDtoToEntityThrowsExceptionSourceNotFound() {

        //  arrange
//        when(typeRepository.findByExtId("source")).thenReturn(Optional.empty());

        //  act
//        try {
//            associationConverter.dtoToEntity(associationDto);
//        } catch (IllegalArgumentException iae) {
            //  assert
//            Mockito.verify(typeRepository, Mockito.times(1)).findByExtId("source");
//            Mockito.verify(typeRepository, Mockito.times(0)).findByExtId("target");
//        }
    }

    @Test
    void testDtoToEntityThrowsExceptionTargetNotFound() {

        //  arrange
        //when(typeRepository.findByExtId("source")).thenReturn(Optional.of(sourceTypeEntity));
        //when(typeRepository.findByExtId("target")).thenReturn(Optional.empty());

        //  act
        try {
            associationConverter.dtoToEntity(associationDto);
        } catch (IllegalArgumentException iae) {
            //  assert
            //Mockito.verify(typeRepository, Mockito.times(1)).findByExtId("source");
            //Mockito.verify(typeRepository, Mockito.times(1)).findByExtId("target");
        }
    }

    @Test
    void testEntityToDto() {

        //  act
        TypeAssociationDto resultAssociationDto = associationConverter.entityToDto(associationEntity);

        //  assert
        Assert.assertEquals(resultAssociationDto.getId(), associationEntity.getExtId());
        Assert.assertEquals(resultAssociationDto.getName(), associationEntity.getName());
        //Assert.assertEquals(resultAssociationDto.getSourceType().getId(), sourceTypeEntity.getExtId());
        //Assert.assertEquals(resultAssociationDto.getTargetType().getId(), targetTypeEntity.getExtId());
    }
}
