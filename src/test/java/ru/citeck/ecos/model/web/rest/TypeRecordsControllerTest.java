package ru.citeck.ecos.model.web.rest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.citeck.ecos.model.EcosModelApp;
import ru.citeck.ecos.records2.request.rest.RestHandler;
import ru.citeck.ecos.records2.spring.web.rest.RecordsRestApi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Roman Makarskiy
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = EcosModelApp.class)
@ActiveProfiles(profiles = "test-type-data")
public class TypeRecordsControllerTest {

    private static final String PATH_TO_RESPONSE_DATA = "/controller/type/response/";
    private static final String CURRENT_TYPE_ACTION_ATT = "actions[]?id";
    private static final String TYPE_ACTION_WITH_INHERIT_ATT = "_actions[]?id";

    private MockMvc mockRecordsApi;

    @Autowired
    private RestHandler restHandler;

    @Before
    public void setup() {
        RecordsRestApi recordsApi = new RecordsRestApi(restHandler);
        this.mockRecordsApi = MockMvcBuilders
            .standaloneSetup(recordsApi)
            .build();
    }

    @Test
    public void typeWithActionsSchema() throws Exception {
        performQueryAndCheckResponse("type-tree", CURRENT_TYPE_ACTION_ATT, "type-tree-response.json");
    }

    @Test
    public void inheritActionsWithActionsAtt() throws Exception {
        performQueryAndCheckResponse("type-second", CURRENT_TYPE_ACTION_ATT,
            "second-type-action-inherit-response-with-non-inherited-actions.json");
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
        String request = "{\n" +
            "    \"record\": \"type@" + recordId + "\",\n" +
            "    \"attributes\": {\n" +
            "        \"name\": \"name\",\n" +
            "        \"parent\": \"parent?id\",\n" +
            "        \"description\": \"description\",\n" +
            "        \"form\": \"form?id\",\n" +
            "        \"inheritActions\": \"inheritActions?bool\",\n" +
            "        \"actions\": \"" + actionAtt + "\"\n" +
            "    }\n" +
            "}";
        String createTypeResponseJson = TestUtil.getJsonFromResource(PATH_TO_RESPONSE_DATA + responseFile);

        mockRecordsApi.perform(
            post(TestUtil.URL_RECORDS_QUERY)
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(request))
            .andExpect(status().isOk())
            .andExpect(content().json(createTypeResponseJson, false));
    }
}
