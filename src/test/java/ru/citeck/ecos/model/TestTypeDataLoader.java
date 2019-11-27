package ru.citeck.ecos.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import ru.citeck.ecos.model.dto.TypeDto;
import ru.citeck.ecos.model.service.TypeService;
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
            String dataToLoad = TestUtil.getJsonFromResource("/controller/type/ecos-type-controller-test-data.json");

            TypeDto[] types = objectMapper.readValue(dataToLoad, TypeDto[].class);

            log.info("================ CREATE TEST TYPE DATA ================");

            for (TypeDto type : types) {
                log.info(type.toString());
                typeService.update(type);
            }

            log.info("======================== END ==========================");
        };
    }

}
