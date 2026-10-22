package ru.citeck.ecos.model.service.keycloak

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.citeck.ecos.commons.json.Json

class KeycloakUserServiceTest {

    @Test
    fun userFromFederationProviderIsFederated() {
        val user = Json.mapper.newObjectNode()
        user.put("id", "user-id")
        user.put("federationLink", "ldap-provider-id")

        assertThat(KeycloakUserService.isFederated(user)).isTrue()
    }

    @Test
    fun localUserIsNotFederated() {
        val user = Json.mapper.newObjectNode()
        user.put("id", "user-id")
        assertThat(KeycloakUserService.isFederated(user)).isFalse()

        user.putNull("federationLink")
        assertThat(KeycloakUserService.isFederated(user)).isFalse()

        user.put("federationLink", "")
        assertThat(KeycloakUserService.isFederated(user)).isFalse()
    }
}
