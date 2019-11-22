package ru.citeck.ecos.model.converter;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.model.converter.impl.AssociationConverter;
import ru.citeck.ecos.model.domain.AssociationEntity;
import ru.citeck.ecos.model.domain.TypeEntity;
import ru.citeck.ecos.model.dto.AssociationDto;
import ru.citeck.ecos.model.dto.TypeDto;
import ru.citeck.ecos.model.repository.TypeRepository;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Optional;

import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
public class AssociationConverterTest {

    @MockBean
    private TypeRepository typeRepository;

    private AssociationConverter associationConverter;

    private AssociationDto associationDto;
    private AssociationEntity associationEntity;
    private TypeEntity sourceTypeEntity;
    private TypeEntity targetTypeEntity;

    @BeforeEach
    void setUp() {
        associationConverter = new AssociationConverter(typeRepository);

        associationDto = new AssociationDto();
        associationDto.setId("association");
        associationDto.setName("name");
        associationDto.setTitle("title");
        associationDto.setSourceType(RecordRef.create("type", "source"));
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
        associationEntity.setId(1L);
        associationEntity.setExtId("association");
        associationEntity.setTitle("title");
        associationEntity.setName("name");
        associationEntity.setSource(sourceTypeEntity);
        associationEntity.setTarget(targetTypeEntity);
    }

    @Test
    void testDtoToEntity() {

        //  arrange
        when(typeRepository.findByExtId("source")).thenReturn(Optional.of(sourceTypeEntity));
        when(typeRepository.findByExtId("target")).thenReturn(Optional.of(targetTypeEntity));

        //  act
        AssociationEntity resultAssociationEntity = associationConverter.dtoToEntity(associationDto);

        //  assert
        Assert.assertEquals(resultAssociationEntity.getExtId(), associationDto.getId());
        Assert.assertEquals(resultAssociationEntity.getName(), associationDto.getName());
        Assert.assertEquals(resultAssociationEntity.getTitle(), associationDto.getTitle());
        Assert.assertEquals(resultAssociationEntity.getSource(), sourceTypeEntity);
        Assert.assertEquals(resultAssociationEntity.getTarget(), targetTypeEntity);
    }

    @Test
    void testDtoToEntityThrowsExceptionSourceNotFound() {

        //  arrange
        when(typeRepository.findByExtId("source")).thenReturn(Optional.empty());

        //  act
        try {
            associationConverter.dtoToEntity(associationDto);
        } catch (IllegalArgumentException iae) {
            //  assert
            Mockito.verify(typeRepository, Mockito.times(1)).findByExtId("source");
            Mockito.verify(typeRepository, Mockito.times(0)).findByExtId("target");
        }
    }

    @Test
    void testDtoToEntityThrowsExceptionTargetNotFound() {

        //  arrange
        when(typeRepository.findByExtId("source")).thenReturn(Optional.of(sourceTypeEntity));
        when(typeRepository.findByExtId("target")).thenReturn(Optional.empty());

        //  act
        try {
            associationConverter.dtoToEntity(associationDto);
        } catch (IllegalArgumentException iae) {
            //  assert
            Mockito.verify(typeRepository, Mockito.times(1)).findByExtId("source");
            Mockito.verify(typeRepository, Mockito.times(1)).findByExtId("target");
        }
    }

    @Test
    void testEntityToDto() {

        //  act
        AssociationDto resultAssociationDto = associationConverter.entityToDto(associationEntity);

        //  assert
        Assert.assertEquals(resultAssociationDto.getId(), associationEntity.getExtId());
        Assert.assertEquals(resultAssociationDto.getName(), associationEntity.getName());
        Assert.assertEquals(resultAssociationDto.getTitle(), associationEntity.getTitle());
        Assert.assertEquals(resultAssociationDto.getSourceType().getId(), sourceTypeEntity.getExtId());
        Assert.assertEquals(resultAssociationDto.getTargetType().getId(), targetTypeEntity.getExtId());
    }
}
