package ru.citeck.ecos.model.service.keycloak

import lombok.extern.slf4j.Slf4j
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.CredentialRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ru.citeck.ecos.data.sql.records.listener.DbRecordChangedEvent
import ru.citeck.ecos.data.sql.records.listener.DbRecordCreatedEvent
import ru.citeck.ecos.data.sql.records.listener.DbRecordDeletedEvent
import ru.citeck.ecos.records3.RecordsService
import java.util.*
import javax.annotation.PostConstruct


@Slf4j
@Service
class KeycloakUserService(
    private val recordsService: RecordsService
) {

    private val keycloakUserAttributesList = listOf("id", "firstName", "lastName", "email", "personDisabled" )
    private lateinit var keycloak: Keycloak
    private lateinit var realmResource: RealmResource

    @Value("\${ecos.idp.default-realm}")
    lateinit var defaultRealm: String

    @Value("\${ecos.idp.keycloakServerUrl}")
    lateinit var keycloakServerUrl: String

    @Value("\${ecos.idp.keycloakAdminPassword}")
    lateinit var keycloakAdminPassword: String

    @Value("\${ecos.idp.keycloakDefaultUserPassword}")
    lateinit var keycloakDefaultUserPassword: String


    @PostConstruct
    fun init() {
        keycloak = Keycloak.getInstance(
            keycloakServerUrl,
            "master",
            "admin",
            keycloakAdminPassword,
            "admin-cli"
        )
        realmResource = keycloak.realm(defaultRealm)
    }

    fun createUser(event: DbRecordCreatedEvent) {
        val userAtts = recordsService.getAtts(event.record, keycloakUserAttributesList)

        val user = UserRepresentation()
        user.setUsername(userAtts.getAtt("id").asText())
        user.setFirstName(userAtts.getAtt("firstName").asText())
        user.setLastName(userAtts.getAtt("lastName").asText())
        user.setEmail(userAtts.getAtt("email").asText())
        user.setEnabled(!userAtts.getAtt("personDisabled").asBoolean())

        val credential = CredentialRepresentation()
        credential.type = CredentialRepresentation.PASSWORD
        credential.value = keycloakDefaultUserPassword
        user.credentials = Arrays.asList(credential)

        realmResource.users().create(user);
    }

    fun updateUser(event: DbRecordChangedEvent) {
        val updatedUserAtts = recordsService.getAtts(event.record, keycloakUserAttributesList)

        val users = realmResource.users().search(updatedUserAtts.getAtt("id").asText())
        if (!users.isEmpty()) {
            val userToUpdate = users[0]
            userToUpdate.setFirstName(updatedUserAtts.getAtt("firstName").asText())
            userToUpdate.setLastName(updatedUserAtts.getAtt("lastName").asText())
            userToUpdate.setEmail(updatedUserAtts.getAtt("email").asText())
            userToUpdate.setEnabled(!updatedUserAtts.getAtt("personDisabled").asBoolean())

            realmResource.users().get(userToUpdate.getId()).update(userToUpdate);
        }
    }

    fun deleteUser(event: DbRecordDeletedEvent) {
        val userName = recordsService.getAtt(event.record, "id").asText()

        val users = realmResource.users().search(userName)
        if (!users.isEmpty()) {
            val userId = users[0].id
            realmResource.users().delete(userId)
        }
    }
}
