package ru.citeck.ecos.model.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.records3.RecordsProperties;

@Configuration
public class ApplicationConfig {

    @Bean
    @ConfigurationProperties(prefix = "ecos-model.ecos-records")
    public RecordsProperties recordsProperties() {
        return new RecordsProperties();
    }
}

