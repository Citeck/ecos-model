package ru.citeck.ecos.model.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import ru.citeck.ecos.apps.app.module.type.type.action.EvaluatorDto;
import ru.citeck.ecos.model.converter.impl.EvaluatorConverter;
import ru.citeck.ecos.model.domain.EvaluatorEntity;

import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
public class EvaluatorConverterTest {

    @MockBean
    private Converter<String, JsonNode> nodeConverter;

    private EvaluatorConverter evaluatorConverter;

    private EvaluatorEntity evaluatorEntity;
    private EvaluatorDto evaluatorDto;

    private JsonNode config;

    @BeforeEach
    void setUp() {
        evaluatorConverter = new EvaluatorConverter(nodeConverter);

        evaluatorEntity = new EvaluatorEntity();
        evaluatorEntity.setExtId("evaluator");
        evaluatorEntity.setConfigJson("config");

        config = new TextNode("config");

        evaluatorDto = new EvaluatorDto();
        evaluatorDto.setId("evaluator");
        evaluatorDto.setConfig(config);
    }

    @Test
    void testDtoToEntity() {

        //  arrange
        when(nodeConverter.targetToSource(config)).thenReturn("config");

        //  act
        EvaluatorEntity resultEvaluatorEntity = evaluatorConverter.sourceToTarget(evaluatorDto);

        //  assert
        Assert.assertEquals(resultEvaluatorEntity.getExtId(), evaluatorDto.getId());
        Assert.assertEquals(resultEvaluatorEntity.getConfigJson(), config.asText());
    }

    @Test
    void testEntityToDto() {

        //  arrange
        when(nodeConverter.sourceToTarget("config")).thenReturn(config);

        //  act
        EvaluatorDto resultEvaluatorDto = evaluatorConverter.targetToSource(evaluatorEntity);

        //  assert
        Assert.assertEquals(resultEvaluatorDto.getId(), evaluatorEntity.getExtId());
        Assert.assertEquals(resultEvaluatorDto.getConfig(), config);
    }
}
