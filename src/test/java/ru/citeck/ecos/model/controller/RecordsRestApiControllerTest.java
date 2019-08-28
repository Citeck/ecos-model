package ru.citeck.ecos.model.controller;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.junit4.SpringRunner;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.rest.QueryBody;

import java.util.HashMap;
import java.util.Map;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RecordsRestApiControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void testRecordsQuery() {
        //arrange
        Map<String, String> map = new HashMap<>();

        RecordsQuery recordsQuery = new RecordsQuery();

        QueryBody body = new QueryBody();
        body.setAttributes(map);
        body.setQuery(recordsQuery);

        //act
        Object response = restTemplate.postForObject("/api/records/query", body, Object.class);

        //assert
//        Assert.assertEquals();
    }
}
