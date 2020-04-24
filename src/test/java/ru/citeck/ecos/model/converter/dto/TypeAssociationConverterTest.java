package ru.citeck.ecos.model.converter.dto;

import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.association.converter.TypeAssociationConverter;
import ru.citeck.ecos.model.association.domain.AssociationEntity;
import ru.citeck.ecos.model.association.dto.AssocDirection;
import ru.citeck.ecos.model.association.dto.AssociationDto;
import ru.citeck.ecos.model.type.domain.TypeEntity;
import ru.citeck.ecos.model.type.records.dao.TypeRecordsDao;
import ru.citeck.ecos.records2.RecordRef;

import java.util.UUID;

@ExtendWith(SpringExtension.class)
public class TypeAssociationConverterTest {

    private TypeAssociationConverter typeAssociationConverter;

    private AssociationEntity entity;
    private AssociationDto dto;

    @BeforeEach
    void setUp() {
        typeAssociationConverter = new TypeAssociationConverter();

        TypeEntity targetTypeEntity = new TypeEntity();
        targetTypeEntity.setExtId("targetEntityId");

        TypeEntity sourceTypeEntity = new TypeEntity();
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
        dto.setTarget(RecordRef.create(TypeRecordsDao.ID, targetTypeEntity.getExtId()));
    }

    @Test
    void testDtoToEntity() {

        //  act
        AssociationEntity resultEntity = typeAssociationConverter.dtoToEntity(dto);

        //  assert
        Assert.assertEquals(dto.getId(), resultEntity.getExtId());
        Assert.assertEquals(dto.getName(), Json.getMapper().read(resultEntity.getName(), MLText.class));
        Assert.assertEquals(dto.getDirection(), resultEntity.getDirection());
    }

    @Test
    void testDtoToEntityWithoutExtId() {

        //  arrange
        dto.setId("");

        //  act
        AssociationEntity resultEntity = typeAssociationConverter.dtoToEntity(dto);

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
