package ru.citeck.ecos.model.converter;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.apps.module.ModuleRef;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.model.dto.TypeAssociationDto;
import ru.citeck.ecos.model.eapps.listener.AssocDirection;
import ru.citeck.ecos.model.eapps.listener.AssociationDto;
import ru.citeck.ecos.records2.RecordRef;

@ExtendWith(SpringExtension.class)
public class EappsAssociationConverterTest {

    private EappsAssociationConverter converter;

    private TypeAssociationDto localDto;
    private AssociationDto eappsDto;

    @BeforeEach
    void setUp() {

        converter = new EappsAssociationConverter();

        localDto = new TypeAssociationDto();
        localDto.setId("assocId");
        localDto.setName(new MLText("name"));
        localDto.setTargetType(RecordRef.create("type", "targetTypeId"));
        localDto.setDirection(AssocDirection.TARGET);

        eappsDto = new AssociationDto();
        eappsDto.setId("assocId");
        eappsDto.setName(new MLText("name"));
        eappsDto.setDirection(AssocDirection.TARGET);
        eappsDto.setTarget(ModuleRef.create("TypeModule", "targetTypeId"));
    }

    @Test
    void testSourceToTarget() {

        //  act
        TypeAssociationDto resultDto = converter.sourceToTarget(eappsDto);

        //  assert
        Assert.assertEquals(eappsDto.getId(), resultDto.getId());
        Assert.assertEquals(eappsDto.getDirection(), resultDto.getDirection());
        Assert.assertEquals(eappsDto.getName(), resultDto.getName());
        Assert.assertEquals(eappsDto.getTarget().getId(), resultDto.getTargetType().getId());
    }

    @Test
    void testSourceToTargetWithoutTargetException() {

        //  arrange
        eappsDto.setTarget(null);

        //  act
        try {
            converter.sourceToTarget(eappsDto);
        } catch (IllegalArgumentException iae) {
            //  assert
            Assert.assertEquals(iae.getMessage(), "Association with id: 'assocId' in TypeModule have field " +
                "'targetType' with null value!");
        }
    }

    @Test
    void testTargetToSource() {

        //  act
        try {
            converter.targetToSource(localDto);
        } catch (UnsupportedOperationException iae) {
            //  assert
            Assert.assertEquals(iae.getMessage(), "Convert local dto to eapps dto is not support!");
        }
    }
}
