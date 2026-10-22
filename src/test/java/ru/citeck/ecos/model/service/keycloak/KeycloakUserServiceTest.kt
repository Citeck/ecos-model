package ru.citeck.ecos.model.service.keycloak

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.keycloak.representations.idm.UserRepresentation

class KeycloakUserServiceTest {

    @Test
    fun userFromFederationProviderIsFederated() {
        val user = UserRepresentation()
        user.federationLink = "ldap-provider-id"

        assertThat(KeycloakUserService.isFederated(user)).isTrue()
    }

    @Test
    fun localUserIsNotFederated() {
        assertThat(KeycloakUserService.isFederated(UserRepresentation())).isFalse()

        val user = UserRepresentation()
        user.federationLink = ""
        assertThat(KeycloakUserService.isFederated(user)).isFalse()
    }
}
