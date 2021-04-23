package ru.citeck.ecos.model;

import ecos.com.fasterxml.jackson210.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import ru.citeck.ecos.model.type.dto.TypeDef;
import ru.citeck.ecos.model.type.service.TypeService;
import ru.citeck.ecos.model.web.rest.TestUtil;

/**
 * @author Roman Makarskiy
 */
@Slf4j
@Profile({"test-type-data"})
@Configuration
public class TestTypeDataLoader {

    @Bean
    public CommandLineRunner dataLoader(TypeService typeService, ObjectMapper objectMapper) {
        return args -> {
            String dataToLoad = TestUtil.getFromResource("/controller/type/ecos-type-controller-test-data.json");

            TypeDef[] types = objectMapper.readValue(dataToLoad, TypeDef[].class);

            log.info("================ CREATE TEST TYPE DATA ================");

            for (TypeDef type : types) {
                log.info(type.toString());
                typeService.save(type);
            }

            log.info("======================== END ==========================");
        };
    }
}
