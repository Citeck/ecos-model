package ru.citeck.ecos.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import ru.citeck.ecos.model.dto.EcosTypeDto;
import ru.citeck.ecos.model.service.EcosTypeService;
import ru.citeck.ecos.model.web.rest.TestUtil;

/**
 * @author Roman Makarskiy
 */
@Slf4j
@Profile({"test-type-data"})
@Configuration
public class TestTypeDataLoader {

    @Bean
    public CommandLineRunner dataLoader(EcosTypeService ecosTypeService, ObjectMapper objectMapper) {
        return args -> {
            String dataToLoad = TestUtil.getJsonFromResource("/controller/type/ecos-type-controller-test-data.json");

            EcosTypeDto[] types = objectMapper.readValue(dataToLoad, EcosTypeDto[].class);

            log.info("================ CREATE TEST TYPE DATA ================");

            for (EcosTypeDto type : types) {
                log.info(type.toString());
                ecosTypeService.update(type);
            }

            log.info("======================== END ==========================");
        };
    }

}
