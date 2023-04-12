package ru.citeck.ecos.model.domain.doceditor.onlyoffice

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.webapp.lib.env.EcosWebAppEnvironment

@Configuration
class OnlyOfficeAppConfig(
    private val ecosEnv: EcosWebAppEnvironment
) {

    @Bean
    fun onlyOfficeConverterAppProps(): OnlyOfficeAppProps {
        return ecosEnv.getValue("ecos.integrations.onlyoffice", OnlyOfficeAppProps::class.java)
    }
}
