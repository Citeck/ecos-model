package ru.citeck.ecos.model.domain.type;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ru.citeck.ecos.model.EcosModelApp;
import ru.citeck.ecos.model.type.service.TypesService;
import ru.citeck.ecos.model.web.rest.TestUtil;
import ru.citeck.ecos.records3.RecordsServiceFactory;
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Roman Makarskiy
 */
@Disabled
@ExtendWith(EcosSpringExtension.class)
@SpringBootTest(classes = EcosModelApp.class)
@ActiveProfiles(profiles = {"test-type-data", "test"})
public class TypeRecordsControllerTest {

    private static final String PATH_TO_RESPONSE_DATA = "/controller/type/response/";
    private static final String CURRENT_TYPE_ACTION_ATT = "actions[]?id";
    private static final String TYPE_ACTION_WITH_INHERIT_ATT = "_actions[]?id";

    private MockMvc mockRecordsApi;

    //@Autowired
   // private RestHandler restHandler;

    @Autowired
    private TypesService typeService;

    @Autowired
    private RecordsServiceFactory recordsServiceFactory;

    @BeforeEach
    public void setup() {
/*
        RecordsRestApi recordsApi = new RecordsRestApi(recordsServiceFactory);
        this.mockRecordsApi = MockMvcBuilders
            .standaloneSetup(recordsApi)
            .build();

        TypeDef.Builder type = TypeDef.create();
        type.setId("type");
        type.setName(new MLText("type"));
        typeService.save(type.build());*/
    }

    @Test
    public void typeWithActionsSchema() throws Exception {
        performQueryAndCheckResponse("type-tree", CURRENT_TYPE_ACTION_ATT, "type-tree-response.json");
    }

    @Test
    public void inheritActionsWithActionsAtt() throws Exception {
        performQueryAndCheckResponse("type-second", CURRENT_TYPE_ACTION_ATT,
            "second-type-action-inherit-response-with-non-inherited-actions.json", false);
    }

    @Test
    public void inheritActionsWithDoubleInherit() throws Exception {
        performQueryAndCheckResponse("type-second", TYPE_ACTION_WITH_INHERIT_ATT,
            "second-type-action-inherit-response-with-inherited-actions.json");
    }

    @Test
    public void inheritActionsWithTripleInherit() throws Exception {
        performQueryAndCheckResponse("type-third", TYPE_ACTION_WITH_INHERIT_ATT,
            "second-type-action-inherit-response-with-triple-inherited-actions.json");
    }

    @Test
    public void inheritActionsWithNonInheritMiddleSlice() throws Exception {
        performQueryAndCheckResponse("type-third-to-second-non-inherit", TYPE_ACTION_WITH_INHERIT_ATT,
            "second-type-action-inherit-response-with-non-inherited-in-middle-slice-actions.json");
    }

    @Test
    public void inheritActionsWithTripleInheritWithOverridden() throws Exception {
        performQueryAndCheckResponse("type-third-with-overridden", TYPE_ACTION_WITH_INHERIT_ATT,
            "second-type-action-inherit-response-with-triple-inherited-overridden-actions.json");
    }

    @Test
    public void inheritActionsWithDoubleInheritOverridden() throws Exception {
        performQueryAndCheckResponse("type-second-with-overridden", TYPE_ACTION_WITH_INHERIT_ATT,
            "second-type-action-inherit-response-with-inherited-overridden-actions.json");
    }

    private void performQueryAndCheckResponse(String recordId, String actionAtt, String responseFile) throws Exception {
        performQueryAndCheckResponse(recordId, actionAtt, responseFile, true);
    }

    private void performQueryAndCheckResponse(String recordId, String actionAtt, String responseFile, boolean resolved) throws Exception {
        String recRef = "type@" + recordId;
        if (!resolved) {
            recRef = "types-repo@" + recordId;
        }
        String request = "{\n" +
            "    \"record\": \"" + recRef + "\",\n" +
            "    \"attributes\": {\n" +
            "        \"name\": \"name\",\n" +
            "        \"parent\": \"parent?id\",\n" +
            "        \"description\": \"description\",\n" +
            "        \"form\": \"form?id\",\n" +
            "        \"inheritActions\": \"inheritActions?bool\",\n" +
            "        \"actions\": \"" + actionAtt + "\",\n" +
            "        \"config\": \"config?json\",\n" +
            "        \"configFormRef\": \"configFormRef?id\"\n" +
            "    }\n" +
            "}";
        String createTypeResponseJson = TestUtil.getFromResource(PATH_TO_RESPONSE_DATA + responseFile);

        mockRecordsApi.perform(
            post(TestUtil.URL_RECORDS_QUERY)
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(request))
            .andExpect(status().isOk())
            .andExpect(content().json(createTypeResponseJson, false));
    }
}
