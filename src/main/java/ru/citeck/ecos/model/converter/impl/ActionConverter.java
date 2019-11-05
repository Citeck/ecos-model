package ru.citeck.ecos.model.converter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.apps.app.module.type.type.action.ActionDto;
import ru.citeck.ecos.apps.app.module.type.type.action.EvaluatorDto;
import ru.citeck.ecos.model.converter.AbstractDtoConverter;
import ru.citeck.ecos.model.domain.ActionEntity;
import ru.citeck.ecos.model.domain.EvaluatorEntity;

@Component
public class ActionConverter extends AbstractDtoConverter<ActionDto, ActionEntity> {

    private final NodeConverter nodeConverter;
    private final EvaluatorConverter evaluatorConverter;

    @Autowired
    public ActionConverter(NodeConverter nodeConverter,
                           EvaluatorConverter evaluatorConverter) {
        this.nodeConverter = nodeConverter;
        this.evaluatorConverter = evaluatorConverter;
    }

    public ActionDto entityToDto(ActionEntity action) {
        ActionDto dto = new ActionDto();

        dto.setId(action.getExtId());
        dto.setName(action.getName());
        dto.setKey(action.getKey());
        dto.setType(action.getType());
        dto.setIcon(action.getIcon());
        dto.setOrder(action.getOrder());

        JsonNode configNode = nodeConverter.sourceToTarget(action.getConfigJson());
        dto.setConfig(configNode);

        if (action.getEvaluator() != null) {
            EvaluatorDto evaluatorDto = evaluatorConverter.entityToDto(action.getEvaluator());
            dto.setEvaluator(evaluatorDto);
        }

        return dto;
    }

    public ActionEntity dtoToEntity(ActionDto actionDto) {
        ActionEntity action = new ActionEntity();

        action.setExtId(actionDto.getId());
        action.setName(actionDto.getName());
        action.setKey(actionDto.getKey());
        action.setType(actionDto.getType());
        action.setIcon(actionDto.getIcon());
        action.setOrder(actionDto.getOrder());

        String configString = nodeConverter.targetToSource(actionDto.getConfig());
        action.setConfigJson(configString);

        if (actionDto.getEvaluator() != null) {
            EvaluatorEntity evaluatorEntity = evaluatorConverter.dtoToEntity(actionDto.getEvaluator());
            action.setEvaluator(evaluatorEntity);
        }

        return action;
    }

}
