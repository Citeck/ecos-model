package ru.citeck.ecos.model.ecos.module;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import ru.citeck.ecos.apps.EcosAppsApiFactory;
import ru.citeck.ecos.apps.app.module.type.type.TypeModule;
import ru.citeck.ecos.apps.app.module.type.type.association.AssociationDto;
import ru.citeck.ecos.model.EcosModelApp;
import ru.citeck.ecos.model.dto.TypeDto;
import ru.citeck.ecos.model.service.TypeService;

import java.util.Collections;

import static org.junit.Assert.assertNotNull;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = EcosModelApp.class)
public class TypePublishingTest {

    @Autowired
    private EcosAppsApiFactory apiFactory;
    @Autowired
    private TypeService typeService;

    @Test
    public void test() throws InterruptedException {

        TypeModule type = new TypeModule();
        type.setId("testId");
        type.setName("name");

        AssociationDto associationDto = new AssociationDto();
        associationDto.setId("test-assoc");
        associationDto.setName("assoc-name");
        type.setAssociations(Collections.singletonList(associationDto));

        apiFactory.getModuleApi().publishModule("123", type);

        TypeDto dto = null;
        long endTime = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < endTime) {
            try {
                dto = typeService.getByExtId(type.getId());
            } catch (IllegalArgumentException e) {
                //do nothing
            }
            if (dto != null) {
                break;
            }
            Thread.sleep(100);
        }

        assertNotNull(dto);
    }
}
