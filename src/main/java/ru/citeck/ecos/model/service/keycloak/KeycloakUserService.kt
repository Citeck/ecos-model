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
import ru.citeck.ecos.model.lib.authorities.AuthorityType
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

    private lateinit var props: KeycloakAdminProps

    @PostConstruct
    fun init() {
        props = ecosEnv.getValue("ecos.integrations.keycloakAdmin", KeycloakAdminProps::class.java)
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

    fun updateUser(userName: String) {

        if (!props.enabled) {
            return
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
            return
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
}
