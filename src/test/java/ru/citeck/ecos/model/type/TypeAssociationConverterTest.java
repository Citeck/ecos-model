package ru.citeck.ecos.model.type;

import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.association.converter.AssociationConverter;
import ru.citeck.ecos.model.association.domain.AssociationEntity;
import ru.citeck.ecos.model.association.dto.AssocDirection;
import ru.citeck.ecos.model.association.dto.AssociationDto;
import ru.citeck.ecos.model.type.domain.TypeEntity;
import ru.citeck.ecos.model.type.records.dao.TypeRecordsDaoOld;
import ru.citeck.ecos.model.type.repository.TypeRepository;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
public class TypeAssociationConverterTest {

    @MockBean
    private TypeRepository typeRepository;

    private AssociationConverter typeAssociationConverter;

    private AssociationEntity entity;
    private AssociationDto dto;
    private TypeEntity targetTypeEntity;
    private TypeEntity sourceTypeEntity;

    @BeforeEach
    void setUp() {
        typeAssociationConverter = new AssociationConverter(typeRepository);

        targetTypeEntity = new TypeEntity();
        targetTypeEntity.setExtId("targetEntityId");

        sourceTypeEntity = new TypeEntity();
        sourceTypeEntity.setExtId("sourceEntityId");

        entity = new AssociationEntity();
        entity.setExtId("assocId");
        entity.setName("assoc-name");
        entity.setDirection(AssocDirection.TARGET);
        entity.setTarget(targetTypeEntity);
        entity.setSource(sourceTypeEntity);

        dto = new AssociationDto();
        dto.setId("assocId");
        dto.setName(new MLText("assoc-name"));
        dto.setDirection(AssocDirection.TARGET);
        dto.setTarget(RecordRef.create("emodel", TypeRecordsDaoOld.ID, targetTypeEntity.getExtId()));
    }

    @Test
    void testDtoToEntity() {

        // arrange
//        when(typeRepository.findByExtId("sourceEntityId")).thenReturn(Optional.of(sourceTypeEntity));
        when(typeRepository.findByExtId("targetEntityId")).thenReturn(Optional.of(targetTypeEntity));

        //  act
        AssociationEntity resultEntity = typeAssociationConverter.dtoToEntity(sourceTypeEntity, dto);

        //  assert
        Assert.assertEquals(dto.getId(), resultEntity.getExtId());
        Assert.assertEquals(dto.getName(), Json.getMapper().read(resultEntity.getName(), MLText.class));
        Assert.assertEquals(dto.getDirection(), resultEntity.getDirection());
        Assert.assertEquals("sourceEntityId", resultEntity.getSource().getExtId());
        Assert.assertEquals("targetEntityId", resultEntity.getTarget().getExtId());
    }

    @Test
    void testDtoToEntityWithoutExtId() {

        //  arrange
        dto.setId("");
//        when(typeRepository.findByExtId("sourceEntityId")).thenReturn(Optional.of(sourceTypeEntity));
        when(typeRepository.findByExtId("targetEntityId")).thenReturn(Optional.of(targetTypeEntity));

        //  act
        AssociationEntity resultEntity = typeAssociationConverter.dtoToEntity(sourceTypeEntity, dto);

        //  assert
        Assert.assertEquals(dto.getName(), Json.getMapper().read(resultEntity.getName(), MLText.class));
        Assert.assertEquals(dto.getDirection(), resultEntity.getDirection());

        // check that id it is generated UUID
        Assertions.assertThatCode(() -> UUID.fromString(resultEntity.getExtId())).doesNotThrowAnyException();
    }

    @Test
    void testEntityToDto() {
        AssociationDto resultDto = typeAssociationConverter.entityToDto(entity);
        Assert.assertEquals(dto, resultDto);
    }
}
