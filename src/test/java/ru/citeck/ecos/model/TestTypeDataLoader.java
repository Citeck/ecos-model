package ru.citeck.ecos.model;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.type.service.TypesService;
import ru.citeck.ecos.model.web.rest.TestUtil;
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef;

/**
 * @author Roman Makarskiy
 */
@Slf4j
@Profile({"test-type-data"})
@Configuration
public class TestTypeDataLoader {

    @Bean
    public CommandLineRunner dataLoader(TypesService typeService) {
        return args -> {
            String dataToLoad = TestUtil.getFromResource("/controller/type/ecos-type-controller-test-data.json");

            TypeDef[] types = Json.getMapper().readNotNull(dataToLoad, TypeDef[].class);

            log.info("================ CREATE TEST TYPE DATA ================");

            for (TypeDef type : types) {
                log.info(type.toString());
                typeService.save(type);
            }

            log.info("======================== END ==========================");
        };
    }
}
