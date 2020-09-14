package ru.citeck.ecos.model.permissions;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.domain.permissions.repo.AttributesPermissionEntity;
import ru.citeck.ecos.model.domain.permissions.service.converter.AttributesPermissionConverter;
import ru.citeck.ecos.model.domain.permissions.dto.AttributesPermissionDto;
import ru.citeck.ecos.model.domain.permissions.dto.AttributesPermissionWithMetaDto;
import ru.citeck.ecos.model.domain.permissions.dto.RuleDto;
import ru.citeck.ecos.model.domain.permissions.repo.AttributesPermissionsRepository;
import ru.citeck.ecos.model.type.domain.TypeEntity;
import ru.citeck.ecos.model.type.repository.TypeRepository;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Optional;

import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
public class AttrPermissionConverterTest {

    private final String rules = "[{\"condition\":{\"att\":\"t:conditionAttr\",\"val\":\"1000000\",\"t\":\"gt\"},\"attributes\":[{\"name\":\"ANY\",\"permissions\":{\"read\":false,\"edit\":false}}]}]";

    @MockBean
    private TypeRepository typeRepository;
    @MockBean
    private AttributesPermissionsRepository attributesPermissionsRepository;

    private AttributesPermissionConverter converter;

    private AttributesPermissionEntity targetEntity;
    private AttributesPermissionWithMetaDto targetDto;
    private TypeEntity typeEntity;

    @BeforeEach
    void setUp() {
        this.converter = new AttributesPermissionConverter(typeRepository, attributesPermissionsRepository);

        typeEntity = new TypeEntity();
        typeEntity.setExtId("type");

        targetEntity = new AttributesPermissionEntity();
        targetEntity.setExtId("testAttrPermId");
        targetEntity.setType(typeEntity);
        targetEntity.setRules(rules);

        targetDto = new AttributesPermissionWithMetaDto();
        targetDto.setId("testAttrPermId");
        targetDto.setTypeRef(RecordRef.create("emodel", "type", "type"));
        targetDto.setRules(Json.getMapper().readList(rules, RuleDto.class));
    }

    @Test
    void testEntityToDto() {

        when(attributesPermissionsRepository.findByExtId("testAttrPermId")).thenReturn(Optional.ofNullable(null));

        AttributesPermissionDto dto = converter.entityToDto(targetEntity);
        Assert.assertEquals(targetDto.getId(), dto.getId());
        Assert.assertEquals(targetDto.getTypeRef(), dto.getTypeRef());
        Assert.assertEquals(targetDto.getRules(), dto.getRules());
    }

    @Test
    void testDtoToEntity() {

        when(typeRepository.findByExtId("type")).thenReturn(Optional.of(typeEntity));

        AttributesPermissionEntity entity = converter.dtoToEntity(targetDto);

        Assert.assertEquals(targetEntity.getExtId(), entity.getExtId());
        Assert.assertEquals(targetEntity.getType(), entity.getType());
        Assert.assertEquals(targetEntity.getRules(), entity.getRules());
    }
}
