package ru.citeck.ecos.model.service.factory;

import ru.citeck.ecos.apps.app.module.type.type.action.ActionDto;
import ru.citeck.ecos.model.domain.ActionEntity;

import static ru.citeck.ecos.model.service.factory.NodeConverter.fromString;
import static ru.citeck.ecos.model.service.factory.NodeConverter.nodeAsString;

public class ActionFactory {


    public static ActionDto toDto(ActionEntity action) {
        ActionDto dto = new ActionDto();

        dto.setId(action.getExtId());
        dto.setName(action.getName());
        dto.setKey(action.getKey());
        dto.setType(action.getType());
        dto.setIcon(action.getIcon());
        dto.setOrder(action.getOrder());

        dto.setConfig(fromString(action.getConfigJson()));

        if (action.getEvaluator() != null) {
            dto.setEvaluator(EvaluatorFactory.fromEvaluator(action.getEvaluator()));
        }

        return dto;
    }

    public static ActionEntity fromDto(ActionDto actionDto) {
        ActionEntity action = new ActionEntity();

        action.setExtId(actionDto.getId());
        action.setName(actionDto.getName());
        action.setKey(action.getKey());
        action.setType(actionDto.getType());
        action.setIcon(actionDto.getIcon());
        action.setOrder(actionDto.getOrder());
        action.setConfigJson(nodeAsString(actionDto.getConfig()));

        if (actionDto.getEvaluator() != null) {
            action.setEvaluator(EvaluatorFactory.fromDto(actionDto.getEvaluator()));
        }

        return action;
    }

}
