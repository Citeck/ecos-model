package ru.citeck.ecos.model.converter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.module.type.type.action.EvaluatorDto;
import ru.citeck.ecos.model.converter.AbstractDtoConverter;
import ru.citeck.ecos.model.domain.EvaluatorEntity;

@Component
public class EvaluatorConverter extends AbstractDtoConverter<EvaluatorDto, EvaluatorEntity> {

    private final NodeConverter nodeConverter;

    @Autowired
    public EvaluatorConverter(NodeConverter nodeConverter) {
        this.nodeConverter = nodeConverter;
    }

    public EvaluatorEntity dtoToEntity(EvaluatorDto evaluatorDTO) {
        EvaluatorEntity evaluator = new EvaluatorEntity();
        evaluator.setExtId(evaluatorDTO.getId());

        String configString = nodeConverter.targetToSource(evaluatorDTO.getConfig());
        evaluator.setConfigJson(configString);

        return evaluator;
    }

    public EvaluatorDto entityToDto(EvaluatorEntity evaluator) {
        EvaluatorDto evaluatorDTO = new EvaluatorDto();
        evaluatorDTO.setId(evaluator.getExtId());

        JsonNode configNode = nodeConverter.sourceToTarget(evaluator.getConfigJson());
        evaluatorDTO.setConfig(configNode);

        return evaluatorDTO;
    }

}
