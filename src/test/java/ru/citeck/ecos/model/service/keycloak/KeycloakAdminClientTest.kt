package ru.citeck.ecos.model.service.keycloak

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import ru.citeck.ecos.commons.json.Json
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drives KeycloakAdminClient against a stub of the Keycloak admin API, so the request
 * shapes stay pinned now that we build them ourselves instead of using the admin client.
 */
class KeycloakAdminClientTest {

    private lateinit var server: HttpServer
    private lateinit var client: KeycloakAdminClient

    private val requests = mutableListOf<RecordedRequest>()
    private val tokensIssued = AtomicInteger()

    /** Set by a test to make the next admin call answer 401 once. */
    private var failNextWithUnauthorized = false

    /** expires_in of the issued token, seconds. */
    private var tokenExpiresIn = 300L

    private var usersResponse = "[]"

    @BeforeEach
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        server.createContext("/auth/realms/master/protocol/openid-connect/token") { exchange ->
            record(exchange)
            val n = tokensIssued.incrementAndGet()
            respond(exchange, 200, """{"access_token":"token-$n","expires_in":$tokenExpiresIn}""")
        }

        server.createContext("/auth/admin/realms/ecos-app/users") { exchange ->
            record(exchange)
            if (failNextWithUnauthorized) {
                failNextWithUnauthorized = false
                respond(exchange, 401, """{"error":"invalid_token"}""")
                return@createContext
            }
            when {
                exchange.requestMethod == "GET" -> respond(exchange, 200, usersResponse)
                exchange.requestMethod == "POST" -> respond(exchange, 201, "")
                else -> respond(exchange, 204, "")
            }
        }

        server.start()

        client = KeycloakAdminClient(
            serverUrl = "http://127.0.0.1:${server.address.port}/auth",
            realm = "ecos-app",
            adminUser = "admin",
            adminPassword = "s3cret"
        )
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `token is requested with the password grant and sent as a bearer`() {
        client.findUsersByName("ivan")

        val token = requests.first { it.path.endsWith("/token") }
        assertEquals("POST", token.method)
        assertEquals("application/x-www-form-urlencoded", token.contentType)
        assertTrue(token.body.contains("grant_type=password"))
        assertTrue(token.body.contains("client_id=admin-cli"))
        assertTrue(token.body.contains("username=admin"))
        assertTrue(token.body.contains("password=s3cret"))
        // the token endpoint lives on master, not on the working realm
        assertTrue(token.path.contains("/realms/master/"))

        val search = requests.first { it.method == "GET" }
        assertEquals("Bearer token-1", search.authorization)
    }

    @Test
    fun `user search is exact and url encoded`() {
        client.findUsersByName("ivan petrov+1")

        val search = requests.first { it.method == "GET" }
        assertTrue(search.query.contains("exact=true"), "search must be exact: ${search.query}")
        assertTrue(search.query.contains("username=ivan+petrov%2B1"), "not encoded: ${search.query}")
    }

    @Test
    fun `search returns the users keycloak sent`() {
        usersResponse = """[{"id":"abc","username":"ivan","firstName":"Ivan"}]"""

        val users = client.findUsersByName("ivan")

        assertEquals(1, users.size)
        assertEquals("abc", users[0].path("id").asText())
        assertEquals("Ivan", users[0].path("firstName").asText())
    }

    @Test
    fun `search tolerates an empty or non array body`() {
        usersResponse = ""
        assertTrue(client.findUsersByName("ivan").isEmpty())

        usersResponse = """{"error":"nope"}"""
        assertTrue(client.findUsersByName("ivan").isEmpty())
    }

    @Test
    fun `create sends the representation as json`() {
        val user = Json.mapper.newObjectNode()
        user.put("username", "ivan")
        user.put("enabled", true)

        client.createUser(user)

        val created = requests.first { it.method == "POST" && it.path.endsWith("/users") }
        assertEquals("application/json", created.contentType)
        val body = Json.mapper.read(created.body)!!
        assertEquals("ivan", body.path("username").asText())
        assertTrue(body.path("enabled").asBoolean())
    }

    @Test
    fun `update and delete address the user by id`() {
        val user = Json.mapper.newObjectNode()
        user.put("firstName", "Ivan")

        client.updateUser("abc-123", user)
        client.deleteUser("abc-123")

        val put = requests.first { it.method == "PUT" }
        assertTrue(put.path.endsWith("/users/abc-123"), "unexpected path: ${put.path}")
        val delete = requests.first { it.method == "DELETE" }
        assertTrue(delete.path.endsWith("/users/abc-123"), "unexpected path: ${delete.path}")
    }

    @Test
    fun `reset password hits the reset-password endpoint`() {
        val credential = Json.mapper.newObjectNode()
        credential.put("type", "password")
        credential.put("value", "new-one")

        client.resetPassword("abc-123", credential)

        val reset = requests.first { it.method == "PUT" }
        assertTrue(reset.path.endsWith("/users/abc-123/reset-password"), "unexpected path: ${reset.path}")
        assertEquals("new-one", Json.mapper.read(reset.body)!!.path("value").asText())
    }

    @Test
    fun `token is reused while it is still valid`() {
        client.findUsersByName("a")
        client.findUsersByName("b")
        client.findUsersByName("c")

        assertEquals(1, tokensIssued.get(), "token must be fetched once and cached")
    }

    @Test
    fun `token is refetched once it expires`() {
        tokenExpiresIn = 1 // below the 30s margin, so it counts as expired immediately

        client.findUsersByName("a")
        client.findUsersByName("b")

        assertEquals(2, tokensIssued.get(), "an expired token must be replaced")
    }

    @Test
    fun `a 401 is retried once with a fresh token`() {
        usersResponse = """[{"id":"abc"}]"""
        failNextWithUnauthorized = true

        val users = client.findUsersByName("ivan")

        assertEquals(1, users.size, "the retry must return the real result")
        assertEquals(2, tokensIssued.get(), "the retry must use a new token")
        val gets = requests.filter { it.method == "GET" }
        assertEquals(2, gets.size)
        assertEquals("Bearer token-1", gets[0].authorization)
        assertEquals("Bearer token-2", gets[1].authorization)
    }

    @Test
    fun `a failed call raises with the status`() {
        server.removeContext("/auth/admin/realms/ecos-app/users")
        server.createContext("/auth/admin/realms/ecos-app/users") { exchange ->
            record(exchange)
            respond(exchange, 500, """{"error":"boom"}""")
        }

        val ex = assertThrows<IllegalStateException> { client.findUsersByName("ivan") }
        assertTrue(ex.message!!.contains("500"), "status must be reported: ${ex.message}")
    }

    @Test
    fun `a failing token request does not leak the credentials`() {
        server.removeContext("/auth/realms/master/protocol/openid-connect/token")
        server.createContext("/auth/realms/master/protocol/openid-connect/token") { exchange ->
            record(exchange)
            respond(exchange, 401, """{"error":"invalid_grant"}""")
        }

        val ex = assertThrows<IllegalStateException> { client.findUsersByName("ivan") }
        assertTrue(ex.message!!.contains("401"))
        assertNull(
            Regex("s3cret").find(ex.message!!),
            "the admin password must not appear in the error: ${ex.message}"
        )
    }

    private fun record(exchange: HttpExchange) {
        requests += RecordedRequest(
            method = exchange.requestMethod,
            path = exchange.requestURI.path,
            query = exchange.requestURI.rawQuery ?: "",
            authorization = exchange.requestHeaders.getFirst("Authorization") ?: "",
            contentType = exchange.requestHeaders.getFirst("Content-Type") ?: "",
            body = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
        )
    }

    private fun respond(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) {
            exchange.responseBody.use { it.write(bytes) }
        } else {
            exchange.responseBody.close()
        }
    }

    private data class RecordedRequest(
        val method: String,
        val path: String,
        val query: String,
        val authorization: String,
        val contentType: String,
        val body: String
    )
}
