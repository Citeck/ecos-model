package ru.citeck.ecos.model.domain.secret.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
class EcosSecretConfiguration {

    @Bean
    fun ecosSecretKeyEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }
}
