package ru.citeck.ecos.model.service.keycloak

import com.fasterxml.jackson.databind.node.ObjectNode
import io.github.oshai.kotlinlogging.KotlinLogging
import ru.citeck.ecos.commons.json.Json
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant

/**
 * The slice of the Keycloak Admin REST API that ecos-model actually needs: find a user by
 * name, create, update, delete, reset password.
 *
 * This used to be org.keycloak:keycloak-admin-client, which was stuck on 12.0.4 and brought
 * keycloak-core plus a RESTEasy 3 stack with it - six unfixable CVEs for six calls. Talking
 * to the REST API directly needs nothing beyond the JDK http client and the platform's Json.
 *
 * Endpoints follow the Keycloak admin API and have been stable since well before 12:
 *   POST   {url}/realms/master/protocol/openid-connect/token
 *   GET    {url}/admin/realms/{realm}/users?username={u}&exact=true
 *   POST   {url}/admin/realms/{realm}/users
 *   PUT    {url}/admin/realms/{realm}/users/{id}
 *   DELETE {url}/admin/realms/{realm}/users/{id}
 *   PUT    {url}/admin/realms/{realm}/users/{id}/reset-password
 */
class KeycloakAdminClient(
    private val serverUrl: String,
    private val realm: String,
    private val adminUser: String,
    private val adminPassword: String,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
) {

    companion object {
        private val log = KotlinLogging.logger {}

        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(30)

        /** Refresh slightly early so a token cannot expire between the check and the call. */
        private val TOKEN_EXPIRATION_MARGIN: Duration = Duration.ofSeconds(30)

        private const val ADMIN_CLIENT_ID = "admin-cli"
        private const val ADMIN_REALM = "master"
    }

    private val baseUrl = serverUrl.trimEnd('/')

    @Volatile
    private var token: String? = null

    @Volatile
    private var tokenExpiresAt: Instant = Instant.EPOCH

    /** Users matching [userName] exactly. Empty when there is no such user. */
    fun findUsersByName(userName: String): List<ObjectNode> {
        val query = "username=" + encode(userName) + "&exact=true"
        val response = send("GET", "$baseUrl/admin/realms/$realm/users?$query", null)
        checkSuccess(response, "search user '$userName'")
        val users = Json.mapper.read(response.body())
        if (users == null || !users.isArray) {
            return emptyList()
        }
        return users.mapNotNull { it as? ObjectNode }
    }

    fun createUser(user: ObjectNode) {
        val response = send("POST", "$baseUrl/admin/realms/$realm/users", user)
        checkSuccess(response, "create user")
    }

    fun updateUser(userId: String, user: ObjectNode) {
        val response = send("PUT", "$baseUrl/admin/realms/$realm/users/${encode(userId)}", user)
        checkSuccess(response, "update user '$userId'")
    }

    fun deleteUser(userId: String) {
        val response = send("DELETE", "$baseUrl/admin/realms/$realm/users/${encode(userId)}", null)
        checkSuccess(response, "delete user '$userId'")
    }

    fun resetPassword(userId: String, credential: ObjectNode) {
        val response = send(
            "PUT",
            "$baseUrl/admin/realms/$realm/users/${encode(userId)}/reset-password",
            credential
        )
        checkSuccess(response, "reset password of user '$userId'")
    }

    /**
     * Retries once on 401: an access token can be revoked or the server restarted between
     * two calls, and a single retry with a fresh token is cheaper than a failed user action.
     */
    private fun send(method: String, url: String, body: ObjectNode?): HttpResponse<String> {
        val response = sendWithToken(method, url, body, getToken(false))
        if (response.statusCode() != 401) {
            return response
        }
        log.debug { "Keycloak returned 401 for $method $url, retrying with a fresh token" }
        return sendWithToken(method, url, body, getToken(true))
    }

    private fun sendWithToken(
        method: String,
        url: String,
        body: ObjectNode?,
        accessToken: String
    ): HttpResponse<String> {

        val bodyPublisher = if (body == null) {
            HttpRequest.BodyPublishers.noBody()
        } else {
            HttpRequest.BodyPublishers.ofString(Json.mapper.toString(body), StandardCharsets.UTF_8)
        }

        val builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(REQUEST_TIMEOUT)
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .method(method, bodyPublisher)

        if (body != null) {
            builder.header("Content-Type", "application/json")
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
    }

    private fun getToken(forceRefresh: Boolean): String {
        val current = token
        if (!forceRefresh && current != null && Instant.now().isBefore(tokenExpiresAt)) {
            return current
        }
        synchronized(this) {
            val cached = token
            if (!forceRefresh && cached != null && Instant.now().isBefore(tokenExpiresAt)) {
                return cached
            }
            return requestToken()
        }
    }

    private fun requestToken(): String {

        val form = "grant_type=password" +
            "&client_id=" + encode(ADMIN_CLIENT_ID) +
            "&username=" + encode(adminUser) +
            "&password=" + encode(adminPassword)

        val request = HttpRequest.newBuilder(
            URI.create("$baseUrl/realms/$ADMIN_REALM/protocol/openid-connect/token")
        )
            .timeout(REQUEST_TIMEOUT)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (response.statusCode() !in 200..299) {
            // the body of a failed token request echoes the request, so it is not logged
            error("Cannot obtain Keycloak admin token. Status: ${response.statusCode()}")
        }

        val parsed = Json.mapper.read(response.body()) ?: error("Empty Keycloak token response")
        val accessToken = parsed.path("access_token").asText("")
        if (accessToken.isEmpty()) {
            error("Keycloak token response has no access_token")
        }

        val expiresIn = parsed.path("expires_in").asLong(60)
        token = accessToken
        tokenExpiresAt = Instant.now()
            .plusSeconds(expiresIn)
            .minus(TOKEN_EXPIRATION_MARGIN)

        return accessToken
    }

    private fun checkSuccess(response: HttpResponse<String>, action: String) {
        if (response.statusCode() !in 200..299) {
            error("Cannot $action in Keycloak. Status: ${response.statusCode()}. Body: ${response.body()}")
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
