package ru.citeck.ecos.model.service.keycloak

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import lombok.extern.slf4j.Slf4j
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.KeycloakBuilder
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.CredentialRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.lib.env.EcosWebAppEnvironment

@Slf4j
@Service
class KeycloakUserService(
    private val recordsService: RecordsService,
    private val ecosEnv: EcosWebAppEnvironment
) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    private lateinit var keycloak: Keycloak
    private lateinit var realmResource: RealmResource

    @Value("\${ecos.idp.default-realm}")
    lateinit var defaultRealm: String

    private lateinit var props: KeycloakAdminProps

    @PostConstruct
    fun init() {
        props = ecosEnv.getValue("ecos.integrations.keycloakAdmin", KeycloakAdminProps::class.java)
        if (props.enabled) {

            val jacksonProvider = CustomResteasyJackson2Provider()
            // Disable this feature to let the old client library work with newer Keycloak versions
            jacksonProvider.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

            val client = ResteasyClientBuilder()
                .register(jacksonProvider, 100)
                .build()

            keycloak = KeycloakBuilder.builder()
                .serverUrl(props.url)
                .realm("master")
                .username(props.user)
                .password(props.password)
                .clientId("admin-cli")
                .resteasyClient(client)
                .build()

            realmResource = keycloak.realm(defaultRealm)

        } else {
            log.info { "Keycloak integration is disabled. Skipping Keycloak initialization." }
        }
    }

    fun isEnabled(): Boolean {
        return props.enabled
    }

    fun updateUser(userName: String) {

        if (!props.enabled) {
            error("Keycloak integration is disabled")
        }

        if (!checkUserAuth(userName)) {
            throw IllegalStateException("Cannot update user '$userName'. User does not have permissions.")
        }

        val personRef = AuthorityType.PERSON.getRef(userName)
        val userAtts = recordsService.getAtts(personRef, KeycloakUserAttributes::class.java)

        val users = realmResource.users().search(userName)
        if (users.isEmpty()) {

            val user = UserRepresentation()
            user.username = userAtts.id
            user.firstName = userAtts.firstName
            user.lastName = userAtts.lastName
            user.email = userAtts.email
            user.isEnabled = !userAtts.personDisabled

            val credential = CredentialRepresentation()
            credential.type = CredentialRepresentation.PASSWORD
            credential.value = userAtts.id
            user.credentials = listOf(credential)

            val requiredActions = ArrayList<String>()
            requiredActions.add("UPDATE_PASSWORD")
            user.requiredActions = requiredActions

            realmResource.users().create(user)
        } else {

            val userToUpdate = users[0]
            userToUpdate.firstName = userAtts.firstName
            userToUpdate.lastName = userAtts.lastName
            userToUpdate.email = userAtts.email
            userToUpdate.isEnabled = !userAtts.personDisabled

            realmResource.users().get(userToUpdate.id).update(userToUpdate)
        }
    }

    fun deleteUser(userName: String) {

        if (!props.enabled) {
            error("Keycloak integration is disabled")
        }

        if (!checkUserAuth(userName)) {
            throw IllegalStateException("Cannot delete user '$userName'. User does not have permissions.")
        }

        val users = realmResource.users().search(userName)
        if (users.isNotEmpty()) {
            val userId = users[0].id
            realmResource.users().delete(userId)
        }
    }

    fun updateUserPassword(userName: String, newPassword: String) {

        if (!props.enabled) {
            throw IllegalStateException(
                "Cannot update user password for '$userName'. " +
                    "Keycloak integration is disabled."
            )
        }
        if (!checkUserAuth(userName)) {
            throw IllegalStateException(
                "Cannot update user password for '$userName'. " +
                    "User does not have permissions."
            )
        }

        val users = realmResource.users().search(userName)
        if (users.isNotEmpty()) {
            val userToUpdate = users[0]
            val credential = CredentialRepresentation()
            credential.type = CredentialRepresentation.PASSWORD
            credential.value = newPassword

            realmResource.users().get(userToUpdate.id).resetPassword(credential)
        } else {
            log.warn("User with username '$userName' not found.")
        }
    }

    private fun checkUserAuth(changedUserName: String): Boolean {
        return AuthContext.isRunAsSystemOrAdmin() || AuthContext.getCurrentUser() == changedUserName
    }

    data class KeycloakUserAttributes(
        val id: String,
        @AttName("firstName!")
        val firstName: String,
        @AttName("lastName!")
        val lastName: String,
        @AttName("email!")
        val email: String,
        @AttName("personDisabled!")
        val personDisabled: Boolean
    )

    data class KeycloakAdminProps(
        val url: String,
        val user: String,
        val password: String,
        val enabled: Boolean
    )

    /**
     * ResteasyClient registers its own ResteasyJackson2Provider by default,
     * so our custom provider gets ignored.
     * To apply a custom ObjectMapper configuration, we create a dedicated subclass.
     */
    private class CustomResteasyJackson2Provider : ResteasyJackson2Provider()
}
