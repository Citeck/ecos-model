package ru.citeck.ecos.model.num;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import ru.citeck.ecos.commands.CommandsService;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.model.EcosModelApp;
import ru.citeck.ecos.model.num.command.GetNextNumber;
import ru.citeck.ecos.model.num.dto.NumTemplateDto;
import ru.citeck.ecos.model.num.service.NumTemplateService;
import ru.citeck.ecos.records2.RecordRef;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = EcosModelApp.class)
public class NumTemplateTest {

    @Autowired
    private NumTemplateService numTemplateService;
    @Autowired
    private CommandsService commandsService;

    @Test
    public void test() {

        NumTemplateDto dto = new NumTemplateDto();
        dto.setId("template-id");
        dto.setName("template-name");
        dto.setCounterKey("counter-${prop}-value");

        numTemplateService.save(dto);

        GetNextNumber.Response result = commandsService.executeSync(new GetNextNumber.Command(
            RecordRef.create("emodel", "num-template", "template-id"),
            ObjectData.create("{\"prop\":\"propValue\"}")
        )).getResultAs(GetNextNumber.Response.class);

        assertEquals(Long.valueOf(1L), result.getNumber());

        result = commandsService.executeSync(new GetNextNumber.Command(
            RecordRef.create("emodel", "num-template", "template-id"),
            ObjectData.create("{\"prop\":\"propValue\"}")
        )).getResultAs(GetNextNumber.Response.class);

        assertEquals(Long.valueOf(2L), result.getNumber());

        result = commandsService.executeSync(new GetNextNumber.Command(
            RecordRef.create("emodel", "num-template", "template-id"),
            ObjectData.create("{\"prop\":\"propValue\"}")
        )).getResultAs(GetNextNumber.Response.class);

        assertEquals(Long.valueOf(3L), result.getNumber());

        result = commandsService.executeSync(new GetNextNumber.Command(
            RecordRef.create("emodel", "num-template", "template-id"),
            ObjectData.create("{\"prop\":\"propValue2\"}")
        )).getResultAs(GetNextNumber.Response.class);

        assertEquals(Long.valueOf(1L), result.getNumber());
    }

    @Test
    public void stressTest() throws InterruptedException {

        int iterationsCount = 1000;
        int threadsCount = 4;

        NumTemplateDto dto = new NumTemplateDto();
        dto.setId("template-mt-id");
        dto.setName("template-name");
        dto.setCounterKey("counter-${prop}-value");

        numTemplateService.save(dto);

        Set<Long> numbers = Collections.newSetFromMap(new ConcurrentHashMap<>());

        Runnable action = () -> {

            for (int i = 0; i < iterationsCount; i++) {

                GetNextNumber.Response result = commandsService.executeSync(new GetNextNumber.Command(
                    RecordRef.create("emodel", "num-template", "template-mt-id"),
                    ObjectData.create("{\"prop\":\"propValue\"}")
                )).getResultAs(GetNextNumber.Response.class);

                if (result == null || result.getNumber() == null) {
                    throw new IllegalStateException("RESULT IS NULL! " + result);
                }
                if (!numbers.add(result.getNumber())) {
                    throw new IllegalStateException("Duplicate number! " + result);
                }
            }
        };

        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < threadsCount; i++) {
            threads.add(new Thread(action));
        }

        threads.forEach(Thread::start);
        for (Thread thread : threads) {
            thread.join();
        }

        GetNextNumber.Response result = commandsService.executeSync(new GetNextNumber.Command(
            RecordRef.create("emodel", "num-template", "template-mt-id"),
            ObjectData.create("{\"prop\":\"propValue\"}")
        )).getResultAs(GetNextNumber.Response.class);

        assertEquals(numbers.size(), (iterationsCount * threadsCount));

        Set<Long> expectedNumbers = new TreeSet<>();
        for (int i = 1; i <= (iterationsCount * threadsCount); i++) {
            expectedNumbers.add((long) i);
        }
        Set<Long> actualNumbers = new TreeSet<>(numbers);

        assertEquals(expectedNumbers, actualNumbers);
        assertEquals(Long.valueOf((iterationsCount * threadsCount) + 1), result.getNumber());
    }
}
