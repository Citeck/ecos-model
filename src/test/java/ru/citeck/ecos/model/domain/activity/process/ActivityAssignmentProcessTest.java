package ru.citeck.ecos.model.domain.activity.process;

import org.graalvm.polyglot.Context;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ActivityAssignmentProcessTest {

    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String ECOS_NS = "http://www.citeck.ru/ecos/bpmn/1.0";

    @ParameterizedTest
    @ValueSource(strings = {"crm-workspace", "user$admin"})
    void createsAssignmentInParentWorkspace(String workspace) throws Exception {
        try (Context context = Context.newBuilder("js").option("engine.WarnInterpreterOnly", "false").build()) {
            context.getBindings("js").putMember("parentWorkspace", workspace);
            context.eval("js", """
                const values = {
                    title: 'Assignment title', text: 'Assignment details',
                    dueDate: '2026-11-04T12:00:00Z', priority: 2,
                    'initiator?str': 'emodel/person@admin',
                    'performer?str': 'emodel/person@admin',
                    '_parent?id': 'emodel/deal@test',
                    '_workspace.id': null,
                    '_parent._workspace?localId': parentWorkspace
                };
                const documentWrites = {};
                const assignmentWrites = {};
                let assignmentSaves = 0;
                const document = {
                    load: mapping => Object.fromEntries(Object.entries(mapping).map(([key, path]) => [key, values[path] ?? null])),
                    att: (key, value) => { documentWrites[key] = value; },
                    save: () => {}
                };
                const Records = {
                    get: ref => {
                        if (ref !== 'emodel/assignment-type@') throw new Error('Unexpected source: ' + ref);
                        return {
                            att: (key, value) => { assignmentWrites[key] = value; },
                            save: () => { assignmentSaves++; return {getId: () => 'emodel/assignment-type@test'}; }
                        };
                    }
                };
                """);
            context.eval("js", script("Activity_0n72bya"));
            assertEquals(workspace, context.eval("js", "assignmentWrites._workspace").asString());
            assertEquals("Assignment title", context.eval("js", "assignmentWrites.title").asString());
            assertEquals("Assignment details", context.eval("js", "assignmentWrites.description").asString());
            assertEquals("2026-11-04T12:00:00Z", context.eval("js", "assignmentWrites.dueDate").asString());
            assertEquals(2, context.eval("js", "assignmentWrites.priority").asInt());
            assertEquals("emodel/person@admin", context.eval("js", "assignmentWrites.performer").asString());
            assertEquals("emodel/deal@test", context.eval("js", "assignmentWrites['assoc:associatedWith']").asString());
            assertFalse(context.eval("js", "assignmentWrites.verify").asBoolean());
            assertEquals(1, context.eval("js", "assignmentSaves").asInt());
            assertEquals("emodel/assignment-type@test", context.eval("js", "documentWrites.assignment").asString());
            context.eval("js", script("Activity_06guyot"));
            assertEquals("created", context.eval("js", "documentWrites._status").asString());
        }
    }

    private String script(String id) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        try (var stream = getClass().getResourceAsStream("/eapps/artifacts/process/bpmn/ecos-activity-process.bpmn.xml")) {
            assertNotNull(stream);
            NodeList tasks = factory.newDocumentBuilder().parse(stream).getElementsByTagNameNS(BPMN_NS, "scriptTask");
            for (int i = 0; i < tasks.getLength(); i++) {
                Element task = (Element) tasks.item(i);
                if (id.equals(task.getAttribute("id"))) {
                    String script = task.getAttributeNS(ECOS_NS, "script");
                    assertFalse(script.isBlank());
                    assertEquals(script, task.getElementsByTagNameNS(BPMN_NS, "script").item(0).getTextContent());
                    return script;
                }
            }
        }
        throw new IllegalArgumentException("Missing task " + id);
    }
}
