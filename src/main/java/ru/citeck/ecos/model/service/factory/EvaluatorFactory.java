package ru.citeck.ecos.model.service.factory;

import ru.citeck.ecos.apps.app.module.type.type.action.EvaluatorDto;
import ru.citeck.ecos.model.domain.EvaluatorEntity;

import static ru.citeck.ecos.model.service.factory.NodeConverter.fromString;
import static ru.citeck.ecos.model.service.factory.NodeConverter.nodeAsString;

public class EvaluatorFactory {

    static EvaluatorEntity fromDto(EvaluatorDto evaluatorDTO) {
        EvaluatorEntity evaluator = new EvaluatorEntity();
        evaluator.setExtId(evaluatorDTO.getId());
        evaluator.setConfigJson(nodeAsString(evaluatorDTO.getConfig()));
        return evaluator;
    }

    static EvaluatorDto fromEvaluator(EvaluatorEntity evaluator) {
        EvaluatorDto evaluatorDTO = new EvaluatorDto();
        evaluatorDTO.setId(evaluator.getExtId());
        evaluatorDTO.setConfig(fromString(evaluator.getConfigJson()));
        return evaluatorDTO;
    }

}
