package ru.citeck.ecos.model.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.POJONode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
public class NodeConverterTest {

    private NodeConverter nodeConverter;

    private JsonNode node;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {

        nodeConverter = new NodeConverter();

        node = new POJONode("node");
        mapper = new ObjectMapper();
    }

    @Test
    void testTargetToSource() {

        //  act
        String result = nodeConverter.targetToSource(node);

        //  assert
        String expectedString = "";
        try {
            expectedString = mapper.writeValueAsString(node);
        } catch (Exception ignore) { }

        Assert.assertEquals(result, expectedString);
    }

    @Test
    void testTargetToSourceException() {

        try {
            //  act
            nodeConverter.targetToSource(new TextNode("123"));
        } catch (RuntimeException goodException) {
            Assert.assertEquals(goodException.getMessage(), "Failed write JsonNode as String");
        }

    }

    @Test
    void testSourceToTarget() {

        //  act
        JsonNode node = nodeConverter.sourceToTarget("\"node\"");

        //  assert
        Assert.assertEquals(node.asText(), "node");
    }

    @Test
    void testSourceToTargetException() {

        try {
            //  act
            nodeConverter.sourceToTarget("123");
        } catch (RuntimeException goodException) {
            Assert.assertEquals(goodException.getMessage(), "Failed read JSON");
        }

    }
}
