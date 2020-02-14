package ru.citeck.ecos.model.converter.dto;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.records2.scalar.MLText;
import ru.citeck.ecos.apps.app.module.type.model.type.AssocDirection;
import ru.citeck.ecos.model.converter.dto.impl.TypeAssociationConverter;
import ru.citeck.ecos.model.dao.TypeRecordsDao;
import ru.citeck.ecos.model.domain.AssociationEntity;
import ru.citeck.ecos.model.domain.TypeEntity;
import ru.citeck.ecos.model.dto.TypeAssociationDto;
import ru.citeck.ecos.model.utils.JsonUtil;
import ru.citeck.ecos.records2.RecordRef;

import java.util.UUID;

@ExtendWith(SpringExtension.class)
public class TypeAssociationConverterTest {

    private TypeAssociationConverter typeAssociationConverter;

    private AssociationEntity entity;
    private TypeAssociationDto dto;

    private TypeEntity targetTypeEntity;
    private TypeEntity sourceTypeEntity;

    @BeforeEach
    void setUp() {
        typeAssociationConverter = new TypeAssociationConverter();

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

        dto = new TypeAssociationDto();
        dto.setId("assocId");
        dto.setName(new MLText("assoc-name"));
        dto.setDirection(AssocDirection.TARGET);
        dto.setTargetType(RecordRef.create(TypeRecordsDao.ID, targetTypeEntity.getExtId()));
    }

    @Test
    void testDtoToEntity() {

        //  act
        AssociationEntity resultEntity = typeAssociationConverter.dtoToEntity(dto);

        //  assert
        Assert.assertEquals(dto.getId(), resultEntity.getExtId());
        Assert.assertEquals(dto.getName(), JsonUtil.safeReadJsonValue(resultEntity.getName(), MLText.class));
        Assert.assertEquals(dto.getDirection(), resultEntity.getDirection());
    }

    @Test
    void testDtoToEntityWithoutExtId() {

        //  arrange
        dto.setId("");

        //  act
        AssociationEntity resultEntity = typeAssociationConverter.dtoToEntity(dto);

        //  assert
        Assert.assertEquals(dto.getName(), JsonUtil.safeReadJsonValue(resultEntity.getName(), MLText.class));
        Assert.assertEquals(dto.getDirection(), resultEntity.getDirection());

        // check that id it is generated UUID
        UUID.fromString(resultEntity.getExtId());
    }

    @Test
    void testEntityToDto() {

        //  act
        TypeAssociationDto resultDto = typeAssociationConverter.entityToDto(entity);

        //  assert
        Assert.assertEquals(resultDto.getId(), entity.getExtId());
        Assert.assertEquals(resultDto.getDirection(), entity.getDirection());
        Assert.assertEquals(resultDto.getName(), JsonUtil.safeReadJsonValue(entity.getName(), MLText.class));
        Assert.assertEquals(resultDto.getTargetType().getId(), entity.getTarget().getExtId());
    }
}
