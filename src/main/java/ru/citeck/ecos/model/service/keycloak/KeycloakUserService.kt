package ru.citeck.ecos.model.service.keycloak

import com.fasterxml.jackson.databind.node.ObjectNode
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import ru.citeck.ecos.commons.json.Json
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.lib.env.EcosWebAppEnvironment

@Service
class KeycloakUserService(
    private val recordsService: RecordsService,
    private val ecosEnv: EcosWebAppEnvironment
) {
    companion object {
        private val log = KotlinLogging.logger {}

        private const val CREDENTIAL_TYPE_PASSWORD = "password"
        private const val REQUIRED_ACTION_UPDATE_PASSWORD = "UPDATE_PASSWORD"
    }

    private var client: KeycloakAdminClient? = null

    @Value("\${ecos.idp.default-realm}")
    lateinit var defaultRealm: String

    private lateinit var props: KeycloakAdminProps

    @PostConstruct
    fun init() {
        props = ecosEnv.getValue("ecos.integrations.keycloakAdmin", KeycloakAdminProps::class.java)
        if (props.enabled) {
            client = KeycloakAdminClient(
                serverUrl = props.url,
                realm = defaultRealm,
                adminUser = props.user,
                adminPassword = props.password
            )
        } else {
            log.info { "Keycloak integration is disabled. Skipping Keycloak initialization." }
        }
    }

    fun isEnabled(): Boolean {
        return props.enabled
    }

    fun updateUser(userName: String) {

        val client = requireEnabled()

        if (!checkUserAuth(userName)) {
            throw IllegalStateException("Cannot update user '$userName'. User does not have permissions.")
        }

        val personRef = AuthorityType.PERSON.getRef(userName)
        val userAtts = recordsService.getAtts(personRef, KeycloakUserAttributes::class.java)

        val users = client.findUsersByName(userName)
        if (users.isEmpty()) {

            val credential = Json.mapper.newObjectNode()
            credential.put("type", CREDENTIAL_TYPE_PASSWORD)
            credential.put("value", userAtts.id)

            val user = Json.mapper.newObjectNode()
            user.put("username", userAtts.id)
            user.set<ObjectNode>("credentials", Json.mapper.newArrayNode().add(credential))
            user.set<ObjectNode>(
                "requiredActions",
                Json.mapper.newArrayNode().add(REQUIRED_ACTION_UPDATE_PASSWORD)
            )
            applyAtts(user, userAtts)

            client.createUser(user)
        } else {
            // Keep every field Keycloak sent us and change only ours, the way the admin
            // client did: a PUT with a partial representation can drop what it omits.
            val userToUpdate = users[0]
            val userId = userToUpdate.path("id").asText("")
            if (userId.isEmpty()) {
                error("Keycloak returned a user without id for username '$userName'")
            }
            applyAtts(userToUpdate, userAtts)
            client.updateUser(userId, userToUpdate)
        }
    }

    fun deleteUser(userName: String) {

        val client = requireEnabled()

        if (!checkUserAuth(userName)) {
            throw IllegalStateException("Cannot delete user '$userName'. User does not have permissions.")
        }

        val users = client.findUsersByName(userName)
        if (users.isNotEmpty()) {
            val userId = users[0].path("id").asText("")
            if (userId.isEmpty()) {
                error("Keycloak returned a user without id for username '$userName'")
            }
            client.deleteUser(userId)
        }
    }

    fun updateUserPassword(userName: String, newPassword: String) {

        val client = client ?: throw IllegalStateException(
            "Cannot update user password for '$userName'. Keycloak integration is disabled."
        )
        if (!checkUserAuth(userName)) {
            throw IllegalStateException(
                "Cannot update user password for '$userName'. " +
                    "User does not have permissions."
            )
        }

        val users = client.findUsersByName(userName)
        if (users.isNotEmpty()) {
            val userId = users[0].path("id").asText("")
            if (userId.isEmpty()) {
                error("Keycloak returned a user without id for username '$userName'")
            }
            val credential = Json.mapper.newObjectNode()
            credential.put("type", CREDENTIAL_TYPE_PASSWORD)
            credential.put("value", newPassword)

            client.resetPassword(userId, credential)
        } else {
            log.warn { "User with username '$userName' not found." }
        }
    }

    private fun applyAtts(user: ObjectNode, atts: KeycloakUserAttributes) {
        user.put("firstName", atts.firstName)
        user.put("lastName", atts.lastName)
        user.put("email", atts.email)
        user.put("enabled", !atts.personDisabled)
    }

    private fun requireEnabled(): KeycloakAdminClient {
        return client ?: error("Keycloak integration is disabled")
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
}
