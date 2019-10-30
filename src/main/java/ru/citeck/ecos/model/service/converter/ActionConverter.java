package ru.citeck.ecos.model.service.converter;

import ru.citeck.ecos.apps.app.module.type.type.action.ActionDto;
import ru.citeck.ecos.model.domain.ActionEntity;

import static ru.citeck.ecos.model.service.converter.NodeConverter.fromString;
import static ru.citeck.ecos.model.service.converter.NodeConverter.nodeAsString;

public class ActionConverter {


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
            dto.setEvaluator(EvaluatorConverter.fromEvaluator(action.getEvaluator()));
        }

        return dto;
    }

    public static ActionEntity fromDto(ActionDto actionDto) {
        ActionEntity action = new ActionEntity();

        action.setExtId(actionDto.getId());
        action.setName(actionDto.getName());
        action.setKey(actionDto.getKey());
        action.setType(actionDto.getType());
        action.setIcon(actionDto.getIcon());
        action.setOrder(actionDto.getOrder());
        action.setConfigJson(nodeAsString(actionDto.getConfig()));

        if (actionDto.getEvaluator() != null) {
            action.setEvaluator(EvaluatorConverter.fromDto(actionDto.getEvaluator()));
        }

        return action;
    }

}
