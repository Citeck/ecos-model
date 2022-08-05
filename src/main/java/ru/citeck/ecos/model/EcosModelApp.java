package ru.citeck.ecos.model;

import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import ru.citeck.ecos.webapp.lib.spring.EcosSpringApplication;

@EnableDiscoveryClient
@SpringBootApplication(scanBasePackageClasses = { EcosModelApp.class })
@EnableJpaRepositories({"ru.citeck.ecos.model.*.repository", "ru.citeck.ecos.model.domain.**.repo"})
public class EcosModelApp {

    public static final String NAME = "emodel";

    /**
     * Main method, used to run the application.
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        new EcosSpringApplication(EcosModelApp.class).run(args);
    }
}
