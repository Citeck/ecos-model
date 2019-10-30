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
import ru.citeck.ecos.model.controller.RecordsRestApiController;
import ru.citeck.ecos.records2.request.rest.RestHandler;

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

    private MockMvc mockRecordsApi;

    @Autowired
    private RestHandler restHandler;

    @Before
    public void setup() {
        RecordsRestApiController recordsApi = new RecordsRestApiController(restHandler);
        this.mockRecordsApi = MockMvcBuilders
            .standaloneSetup(recordsApi)
            .build();
    }

    @Test
    public void typeWithActionsSchema() throws Exception {
        String request = "{\n" +
            "    \"record\": \"emodel/type@type-tree\",\n" +
            "    \"attributes\": {\n" +
            "        \"name\": \"name\",\n" +
            "        \"parent\": \"parent?id\",\n" +
            "        \"tenant\": \"tenant\",\n" +
            "        \"description\": \"description\",\n" +
            "        \"inheritActions\": \"inheritActions?bool\",\n" +
            "        \"actions\": \"actions[]?json\"\n" +
            "    }\n" +
            "}";
        String createTypeResponseJson = TestUtil.getJsonFromResource("/controller/type/type-tree-response.json");

        mockRecordsApi.perform(
            post(TestUtil.URL_RECORDS_QUERY)
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(request))
            .andExpect(status().isOk())
            .andExpect(content().json(createTypeResponseJson, false));
    }

    @Test
    public void inheritActionsWithActionsAtt() throws Exception {
        String request = "{\n" +
            "    \"record\": \"emodel/type@type-second\",\n" +
            "    \"attributes\": {\n" +
            "        \"name\": \"name\",\n" +
            "        \"parent\": \"parent?id\",\n" +
            "        \"tenant\": \"tenant\",\n" +
            "        \"description\": \"description\",\n" +
            "        \"inheritActions\": \"inheritActions?bool\",\n" +
            "        \"actions\": \"actions[]?json\"\n" +
            "    }\n" +
            "}";
        String createTypeResponseJson = TestUtil.getJsonFromResource("/controller/type/" +
            "second-type-action-inherit-response-with-non-inherited-actions.json.json");

        mockRecordsApi.perform(
            post(TestUtil.URL_RECORDS_QUERY)
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(request))
            .andExpect(status().isOk())
            .andExpect(content().json(createTypeResponseJson, false));
    }

    @Test
    public void inheritActionsWithDoubleInherit() throws Exception {
        String request = "{\n" +
            "    \"record\": \"emodel/type@type-second\",\n" +
            "    \"attributes\": {\n" +
            "        \"name\": \"name\",\n" +
            "        \"parent\": \"parent?id\",\n" +
            "        \"tenant\": \"tenant\",\n" +
            "        \"description\": \"description\",\n" +
            "        \"inheritActions\": \"inheritActions?bool\",\n" +
            "        \"actions\": \"_actions[]?json\"\n" +
            "    }\n" +
            "}";
        String createTypeResponseJson = TestUtil.getJsonFromResource("/controller/type/" +
            "second-type-action-inherit-response-with-inherited-actions.json");

        mockRecordsApi.perform(
            post(TestUtil.URL_RECORDS_QUERY)
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(request))
            .andExpect(status().isOk())
            .andExpect(content().json(createTypeResponseJson, false));
    }

    @Test
    public void inheritActionsWithTripleInherit() throws Exception {
        String request = "{\n" +
            "    \"record\": \"emodel/type@type-third\",\n" +
            "    \"attributes\": {\n" +
            "        \"name\": \"name\",\n" +
            "        \"parent\": \"parent?id\",\n" +
            "        \"tenant\": \"tenant\",\n" +
            "        \"description\": \"description\",\n" +
            "        \"inheritActions\": \"inheritActions?bool\",\n" +
            "        \"actions\": \"_actions[]?json\"\n" +
            "    }\n" +
            "}";
        String createTypeResponseJson = TestUtil.getJsonFromResource("/controller/type/" +
            "second-type-action-inherit-response-with-triple-inherited-actions.json.json");

        mockRecordsApi.perform(
            post(TestUtil.URL_RECORDS_QUERY)
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(request))
            .andExpect(status().isOk())
            .andExpect(content().json(createTypeResponseJson, false));
    }

    @Test
    public void inheritActionsWithNonInheritMiddleSlice() throws Exception {
        String request = "{\n" +
            "    \"record\": \"emodel/type@type-third-to-second-non-inherit\",\n" +
            "    \"attributes\": {\n" +
            "        \"name\": \"name\",\n" +
            "        \"parent\": \"parent?id\",\n" +
            "        \"tenant\": \"tenant\",\n" +
            "        \"description\": \"description\",\n" +
            "        \"inheritActions\": \"inheritActions?bool\",\n" +
            "        \"actions\": \"_actions[]?json\"\n" +
            "    }\n" +
            "}";
        String createTypeResponseJson = TestUtil.getJsonFromResource("/controller/type/" +
            "second-type-action-inherit-response-with-non-inherited-in-middle-slice-actions.json");

        mockRecordsApi.perform(
            post(TestUtil.URL_RECORDS_QUERY)
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(request))
            .andExpect(status().isOk())
            .andExpect(content().json(createTypeResponseJson, false));
    }

}
