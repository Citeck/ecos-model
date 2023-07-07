package ru.citeck.ecos.model.service.keycloak

import lombok.extern.slf4j.Slf4j
import mu.KotlinLogging
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.CredentialRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.data.sql.records.listener.DbRecordChangedEvent
import ru.citeck.ecos.data.sql.records.listener.DbRecordCreatedEvent
import ru.citeck.ecos.data.sql.records.listener.DbRecordDeletedEvent
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.webapp.lib.env.EcosWebAppEnvironment
import java.util.*
import javax.annotation.PostConstruct

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

    private var props = ecosEnv.getValue("ecos.integrations.keycloakAdmin", KeycloakAdminProps::class.java)

    @PostConstruct
    fun init() {
        if (props.enabled) {
            keycloak = Keycloak.getInstance(
                props.url,
                "master",
                props.user,
                props.password,
                "admin-cli"
            )
            realmResource = keycloak.realm(defaultRealm)
        } else {
            log.info("Keycloak integration is disabled. Skipping Keycloak initialization.")
        }
    }

    fun createUser(event: DbRecordCreatedEvent) {
        if (!props.enabled) {
            return
        }
        val userAtts = recordsService.getAtts(event.record, KeycloakUserAttributes::class.java)

        val user = UserRepresentation()
        user.setUsername(userAtts.id)
        user.setFirstName(userAtts.firstName)
        user.setLastName(userAtts.lastName)
        user.setEmail(userAtts.email)
        user.setEnabled(!userAtts.personDisabled)

        val credential = CredentialRepresentation()
        credential.type = CredentialRepresentation.PASSWORD
        credential.value = userAtts.id
        user.credentials = Arrays.asList(credential)

        val requiredActions = ArrayList<String>()
        requiredActions.add("UPDATE_PASSWORD")
        user.setRequiredActions(requiredActions)

        realmResource.users().create(user)
    }

    fun updateUser(event: DbRecordChangedEvent) {
        if (!props.enabled) {
            return
        }
        val updatedUserAtts = recordsService.getAtts(event.record, KeycloakUserAttributes::class.java)

        val users = realmResource.users().search(updatedUserAtts.id)
        if (!users.isEmpty()) {
            val userToUpdate = users[0]
            userToUpdate.setFirstName(updatedUserAtts.firstName)
            userToUpdate.setLastName(updatedUserAtts.lastName)
            userToUpdate.setEmail(updatedUserAtts.email)
            userToUpdate.setEnabled(!updatedUserAtts.personDisabled)

            realmResource.users().get(userToUpdate.getId()).update(userToUpdate)
        }
    }

    fun deleteUser(event: DbRecordDeletedEvent) {
        if (!props.enabled) {
            return
        }
        val userAtts = recordsService.getAtts(event.record, KeycloakUserAttributes::class.java)
        val userName = userAtts.id

        val users = realmResource.users().search(userName)
        if (!users.isEmpty()) {
            val userId = users[0].id
            realmResource.users().delete(userId)
        }
    }

    fun updateUserPassword(username: String, newpass: String) {
        if (!props.enabled) {
            throw IllegalStateException("Cannot update user password. Keycloak integration is disabled.")
        }
        if (!checkUserAuth(username)) {
            throw IllegalStateException("Cannot update user password. User does not have permissions.")
        }

        val users = realmResource.users().search(username)
        if (users.isNotEmpty()) {
            val userToUpdate = users[0]
            val credential = CredentialRepresentation()
            credential.type = CredentialRepresentation.PASSWORD
            credential.value = newpass

            realmResource.users().get(userToUpdate.id).resetPassword(credential)
        } else {
            log.warn("User with username '$username' not found.")
        }
    }

    private fun checkUserAuth(username: String): Boolean {
        val runAs = AuthContext.getCurrentRunAsAuth()
        return (AuthContext.isSystemAuth(runAs) || AuthContext.isAdminAuth(runAs) || AuthContext.getCurrentUser() == username)
    }
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
