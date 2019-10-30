package ru.citeck.ecos.model.web.rest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
    public void createTypeWithActions() throws Exception {
        String createTypeJson = TestUtil.getJsonFromResource("/controller/type/create-type-test.json");
        String createTypeResponseJson = TestUtil.getJsonFromResource("/controller/type/create-type-test-response.json");

        mockRecordsApi.perform(
            post(TestUtil.URL_RECORDS_MUTATE)
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(createTypeJson))
            .andExpect(status().isOk())
            .andExpect(content().json(createTypeResponseJson, true));
    }

}
