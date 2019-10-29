package ru.citeck.ecos.model.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import ru.citeck.ecos.apps.app.module.type.type.action.ActionDto;
import ru.citeck.ecos.apps.app.module.type.type.action.EvaluatorDto;
import ru.citeck.ecos.model.EcosModelApp;
import ru.citeck.ecos.model.domain.ActionEntity;
import ru.citeck.ecos.model.dto.EcosTypeDto;
import ru.citeck.ecos.model.repository.ActionRepository;
import ru.citeck.ecos.model.service.converter.ActionConverter;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Roman Makarskiy
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = EcosModelApp.class)
public class ActionsTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private EcosTypeService ecosTypeService;

    @Autowired
    private ActionRepository actionRepository;


    @Test
    public void createAction() throws IOException {
        ActionDto deleteAction = new ActionDto();
        deleteAction.setId("delete");
        deleteAction.setOrder(1.5f);
        deleteAction.setKey("delete-key");
        deleteAction.setName("delete-name");
        deleteAction.setIcon("delete.png");
        deleteAction.setType("server-action");

        EvaluatorDto deleteEvaluator = new EvaluatorDto();
        deleteEvaluator.setId("delete-evaluator");
        JsonNode deleteEvaluatorConfig = OBJECT_MAPPER.readValue("{\n" +
            "  \"permission\": \"Delete\"\n" +
            "}", JsonNode.class);
        deleteEvaluator.setConfig(deleteEvaluatorConfig);

        deleteAction.setEvaluator(deleteEvaluator);

        ActionEntity actionEntity = ActionConverter.fromDto(deleteAction);

        ActionEntity save = actionRepository.save(actionEntity);

        ActionEntity byId = actionRepository.findById(save.getId()).get();

        assertThat(byId, is(actionEntity));
    }

    @Test
    public void createTypeWithAction() throws IOException {
        String typeId = "test-type-id";
        String typeName = "";
        String typeDescription = "type-description";
        String typeTenant = "type-tenant";


        ActionDto deleteAction = new ActionDto();
        deleteAction.setId("delete");
        deleteAction.setOrder(1.5f);
        deleteAction.setKey("delete-key");
        deleteAction.setName("delete-name");
        deleteAction.setIcon("delete.png");
        deleteAction.setType("server-action");

        EvaluatorDto deleteEvaluator = new EvaluatorDto();
        deleteEvaluator.setId("delete-evaluator");
        JsonNode deleteEvaluatorConfig = OBJECT_MAPPER.readValue("{\n" +
            "  \"permission\": \"Delete\"\n" +
            "}", JsonNode.class);
        deleteEvaluator.setConfig(deleteEvaluatorConfig);

        deleteAction.setEvaluator(deleteEvaluator);

        ActionDto viewAction = new ActionDto();
        viewAction.setId("view");
        viewAction.setOrder(0.3f);
        viewAction.setKey("view-key");
        viewAction.setName("view-name");
        viewAction.setIcon("view.png");
        viewAction.setType("server-action");

        EvaluatorDto viewEvaluator = new EvaluatorDto();
        viewEvaluator.setId("view-evaluator");
        JsonNode viewEvaluatorConfig = OBJECT_MAPPER.readValue("{\n" +
            "  \"permission\": \"Read\"\n" +
            "}", JsonNode.class);
        viewEvaluator.setConfig(viewEvaluatorConfig);

        viewAction.setEvaluator(viewEvaluator);


        EcosTypeDto typeDto = new EcosTypeDto();
        typeDto.setId(typeId);
        typeDto.setName(typeName);
        typeDto.setDescription(typeDescription);
        typeDto.setTenant(typeTenant);
        Set<ActionDto> actionDtos = new HashSet<>();
        actionDtos.add(deleteAction);
        actionDtos.add(viewAction);
        typeDto.setActions(actionDtos);

        ecosTypeService.update(typeDto);

        EcosTypeDto found = ecosTypeService.getByExtId(typeId);

        assertThat(found, is(typeDto));
    }

    //TODO: write tests

}
