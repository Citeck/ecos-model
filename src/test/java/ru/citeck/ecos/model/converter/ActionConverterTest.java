package ru.citeck.ecos.model.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.apps.app.module.type.type.action.ActionDto;
import ru.citeck.ecos.apps.app.module.type.type.action.EvaluatorDto;
import ru.citeck.ecos.model.converter.impl.ActionConverter;
import ru.citeck.ecos.model.domain.ActionEntity;
import ru.citeck.ecos.model.domain.EvaluatorEntity;
import ru.citeck.ecos.model.domain.TypeEntity;

import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
public class ActionConverterTest {

    @MockBean
    private Converter<String, JsonNode> nodeConverter;

    @MockBean
    private DtoConverter<EvaluatorDto, EvaluatorEntity> evaluatorConverter;

    private ActionConverter actionConverter;

    private ActionEntity actionEntity;
    private ActionDto actionDto;

    private EvaluatorEntity evaluatorEntity;
    private EvaluatorDto evaluatorDto;
    private JsonNode config;

    @BeforeEach
    void setUp() {

        actionConverter = new ActionConverter(nodeConverter, evaluatorConverter);

        TypeEntity typeEntity = new TypeEntity();
        typeEntity.setExtId("type");

        evaluatorEntity = new EvaluatorEntity();
        evaluatorEntity.setExtId("evaluator");

        actionEntity = new ActionEntity();
        actionEntity.setEcosType(typeEntity);
        actionEntity.setConfigJson("config");
        actionEntity.setEvaluator(evaluatorEntity);
        actionEntity.setExtId("action");
        actionEntity.setIcon("icon");
        actionEntity.setKey("key");
        actionEntity.setName("name");
        actionEntity.setOrder(1);
        actionEntity.setType("type");

        config = new TextNode("config");

        evaluatorDto = new EvaluatorDto();
        evaluatorDto.setId("evaluator");

        actionDto = new ActionDto();
        actionDto.setConfig(config);
        actionDto.setEvaluator(evaluatorDto);
        actionDto.setId("action");
        actionDto.setIcon("icon");
        actionDto.setKey("key");
        actionDto.setName("name");
        actionDto.setOrder(1);
        actionDto.setType("type");
    }

    @Test
    void testEntityToDto() {

        //  arrange
        when(nodeConverter.sourceToTarget("config")).thenReturn(config);
        when(evaluatorConverter.entityToDto(evaluatorEntity)).thenReturn(evaluatorDto);

        // act
        ActionDto resultActionDto = actionConverter.entityToDto(actionEntity);

        // assert
        Assert.assertEquals(resultActionDto.getId(), actionEntity.getExtId());
        Assert.assertEquals(resultActionDto.getIcon(), actionEntity.getIcon());
        Assert.assertEquals(resultActionDto.getEvaluator(), evaluatorDto);
        Assert.assertEquals(resultActionDto.getConfig(), config);
        Assert.assertEquals(resultActionDto.getKey(), actionEntity.getKey());
        Assert.assertEquals(resultActionDto.getName(), actionEntity.getName());
        Assert.assertEquals(resultActionDto.getOrder(), actionEntity.getOrder(), 0.00001);
        Assert.assertEquals(resultActionDto.getType(), actionEntity.getType());
    }

    @Test
    void testEntityToDtoWithoutEvaluator() {

        //  arrange
        actionEntity.setEvaluator(null);
        when(nodeConverter.sourceToTarget("config")).thenReturn(config);

        // act
        ActionDto resultActionDto = actionConverter.entityToDto(actionEntity);

        // assert
        Assert.assertEquals(resultActionDto.getId(), actionEntity.getExtId());
        Assert.assertEquals(resultActionDto.getIcon(), actionEntity.getIcon());
        Assert.assertEquals(resultActionDto.getConfig(), config);
        Assert.assertEquals(resultActionDto.getKey(), actionEntity.getKey());
        Assert.assertEquals(resultActionDto.getName(), actionEntity.getName());
        Assert.assertEquals(resultActionDto.getOrder(), actionEntity.getOrder(), 0.00001);
        Assert.assertEquals(resultActionDto.getType(), actionEntity.getType());
        Mockito.verify(evaluatorConverter, Mockito.times(0)).entityToDto(Mockito.any());
    }

    @Test
    void testDtoToEntity() {

        //  arrange
        when(nodeConverter.targetToSource(config)).thenReturn("config");
        when(evaluatorConverter.dtoToEntity(evaluatorDto)).thenReturn(evaluatorEntity);

        //  act
        ActionEntity resultEntity = actionConverter.dtoToEntity(actionDto);

        //  assert
        Assert.assertEquals(resultEntity.getExtId(), actionDto.getId());
        Assert.assertEquals(resultEntity.getIcon(), actionDto.getIcon());
        Assert.assertEquals(resultEntity.getEvaluator(), evaluatorEntity);
        Assert.assertEquals(resultEntity.getConfigJson(), actionDto.getConfig().asText());
        Assert.assertEquals(resultEntity.getKey(), actionDto.getKey());
        Assert.assertEquals(resultEntity.getName(), actionDto.getName());
        Assert.assertEquals(resultEntity.getOrder(), actionDto.getOrder(), 0.00001);
        Assert.assertEquals(resultEntity.getType(), actionDto.getType());
        Assert.assertNull(resultEntity.getEcosType());
    }

    @Test
    void testDtoToEntityWithoutEvaluator() {

        //  arrange
        actionDto.setEvaluator(null);
        when(nodeConverter.targetToSource(config)).thenReturn("config");

        //  act
        ActionEntity resultEntity = actionConverter.dtoToEntity(actionDto);

        //  assert
        Assert.assertEquals(resultEntity.getExtId(), actionDto.getId());
        Assert.assertEquals(resultEntity.getIcon(), actionDto.getIcon());
        Assert.assertEquals(resultEntity.getConfigJson(), actionDto.getConfig().asText());
        Assert.assertEquals(resultEntity.getKey(), actionDto.getKey());
        Assert.assertEquals(resultEntity.getName(), actionDto.getName());
        Assert.assertEquals(resultEntity.getOrder(), actionDto.getOrder(), 0.00001);
        Assert.assertEquals(resultEntity.getType(), actionDto.getType());
        Assert.assertNull(resultEntity.getEcosType());
        Mockito.verify(evaluatorConverter, Mockito.times(0)).dtoToEntity(Mockito.any());
    }
}
