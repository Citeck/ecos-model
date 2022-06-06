package ru.citeck.ecos.model.permissions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.mock.mockito.MockBean;
import ru.citeck.ecos.model.EcosModelApp;
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.domain.permissions.repo.AttributesPermissionEntity;
import ru.citeck.ecos.model.domain.permissions.service.converter.AttributesPermissionConverter;
import ru.citeck.ecos.model.domain.permissions.dto.AttributesPermissionDto;
import ru.citeck.ecos.model.domain.permissions.dto.AttributesPermissionWithMetaDto;
import ru.citeck.ecos.model.domain.permissions.dto.RuleDto;
import ru.citeck.ecos.model.domain.permissions.repo.AttributesPermissionsRepository;
import ru.citeck.ecos.model.type.repository.TypeEntity;
import ru.citeck.ecos.model.type.repository.TypeRepository;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(EcosSpringExtension.class)
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
        targetDto.setTypeRef(RecordRef.create(EcosModelApp.NAME, "type", "type"));
        targetDto.setRules(Json.getMapper().readList(rules, RuleDto.class));
    }

    @Test
    void testEntityToDto() {

        when(attributesPermissionsRepository.findByExtId("testAttrPermId")).thenReturn(Optional.empty());

        AttributesPermissionDto dto = converter.entityToDto(targetEntity);
        assertEquals(targetDto.getId(), dto.getId());
        assertEquals(targetDto.getTypeRef(), dto.getTypeRef());
        assertEquals(targetDto.getRules(), dto.getRules());
    }

    @Test
    void testDtoToEntity() {

        when(typeRepository.findByExtId("type")).thenReturn(typeEntity);

        AttributesPermissionEntity entity = converter.dtoToEntity(targetDto);

        assertEquals(targetEntity.getExtId(), entity.getExtId());
        assertEquals(targetEntity.getType(), entity.getType());
        assertEquals(targetEntity.getRules(), entity.getRules());
    }
}
