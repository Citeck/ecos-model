package ru.citeck.ecos.model.security;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.context.lib.auth.AuthConstants;

import java.util.Optional;

/**
 * Implementation of {@link AuditorAware} based on Spring Security.
 */
@Component
public class SpringSecurityAuditorAware implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        return Optional.of(SecurityUtils.getCurrentUserLogin().orElse(AuthConstants.SYSTEM_USER));
    }
}
