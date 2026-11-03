package ru.citeck.ecos.model.domain.authorities

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.model.domain.authorities.service.PrivateGroupsService
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.records2.predicate.PredicateService
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.txn.lib.TxnContext
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PrivateGroupsVisibilityTest : AuthoritiesTestBase() {

    @Autowired
    private lateinit var privateGroups: PrivateGroupsService

    @ParameterizedTest
    @ValueSource(strings = ["outside", "team-a", "team-b", "unified", "admin"])
    fun `private person and group visibility survives cache refresh`(actor: String) {
        val groupA = createGroup("private-a", "privateGroup" to true)
        createGroup("private-b", "privateGroup" to true)
        createGroup("public-team")
        createPerson("outside")
        createPerson("alice", "authorityGroups" to listOf(groupA))
        createPerson("bob", "authorityGroups" to listOf(AuthorityType.GROUP.getRef("private-b")))
        createPerson("unified-person", "authorityGroups" to listOf(AuthorityType.GROUP.getRef("UNIFIED_PRIVATE_GROUP")))
        val personIds = listOf("outside", "alice", "bob", "unified-person")
        val groupIds = listOf("private-a", "private-b", "public-team")
        // Creation emits a different event; exercise the existing changed-event contract.
        AuthContext.runAsSystem {
            recordsService.mutateAtt(groupA, "privateGroup", false)
            recordsService.mutateAtt(groupA, "privateGroup", true)
        }
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            assertThat(privateGroups.getPrivateGroups()).contains("GROUP_private-a", "GROUP_private-b")
        }
        val authorities = when (actor) {
            "team-a" -> listOf(AuthRole.USER, "GROUP_private-a")
            "team-b" -> listOf(AuthRole.USER, "GROUP_private-b")
            "unified" -> listOf(AuthRole.USER, "GROUP_UNIFIED_PRIVATE_GROUP")
            "admin" -> listOf(AuthRole.ADMIN)
            else -> listOf(AuthRole.USER)
        }
        fun assertVisible(expectedPersons: List<String>, expectedGroups: List<String>) {
            AuthContext.runAs(actor, authorities) {
                assertThat(query("person", personIds)).containsExactlyInAnyOrderElementsOf(expectedPersons)
                assertThat(query("authority-group", groupIds)).containsExactlyInAnyOrderElementsOf(expectedGroups)
            }
        }
        when (actor) {
            "team-a" -> assertVisible(listOf("alice", "unified-person"), listOf("private-a", "public-team"))
            "team-b" -> assertVisible(listOf("bob", "unified-person"), listOf("private-b", "public-team"))
            "outside" -> assertVisible(listOf("outside", "unified-person"), listOf("public-team"))
            else -> assertVisible(personIds, groupIds)
        }
        // Exercise the real after-commit group-change listener, not a direct cache reset.
        AuthContext.runAsSystem { recordsService.mutateAtt(groupA, "privateGroup", false) }
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            assertThat(privateGroups.getPrivateGroups()).doesNotContain("GROUP_private-a").contains("GROUP_private-b")
        }
        when (actor) {
            "team-b" -> assertVisible(listOf("bob", "unified-person"), listOf("private-a", "private-b", "public-team"))
            "outside", "team-a" -> assertVisible(listOf("outside", "alice", "unified-person"), listOf("private-a", "public-team"))
            else -> assertVisible(personIds, groupIds)
        }
    }

    /** Real Records/transaction/database load. Duration can be raised to the incident's 180 seconds. */
    @Test
    fun `mixed concurrent non-admin requests recover after cache invalidations`() {
        val group = createGroup("load-private", "privateGroup" to true)
        repeat(4) { createPerson("load-user-$it", "authorityGroups" to listOf(group)) }
        createPerson("load-admin")
        // Initialize dynamic comment storage before racing requests against an existing table.
        AuthContext.runAsSystem {
            val comment = recordsService.create(
                "comment",
                mapOf("record" to AuthorityType.PERSON.getRef("load-user-0"), "text" to "Initialize comment schema")
            )
            recordsService.delete(comment)
        }
        val seconds = java.lang.Long.getLong("ecos.private-groups.load.seconds", 15L)
        val executor = Executors.newFixedThreadPool(41)
        val start = CountDownLatch(1)
        val completed = AtomicInteger()
        val invalidations = AtomicInteger()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
        try {
            val requests = (0 until 40).map { client ->
                executor.submit(
                    Callable {
                        check(start.await(5, TimeUnit.SECONDS))
                        val admin = client % 5 == 0
                        val user = if (admin) "load-admin" else "load-user-${client % 4}"
                        val auth = if (admin) listOf(AuthRole.ADMIN) else listOf(AuthRole.USER, "GROUP_load-private")
                        var operation = client
                        while (System.nanoTime() < deadline) {
                            AuthContext.runAs(user, auth) {
                                TxnContext.doInNewTxn {
                                    when (operation++ % 4) {
                                        0, 1 -> recordsService.query(
                                            RecordsQuery.create().withSourceId("workspace")
                                                .withLanguage(PredicateService.LANGUAGE_PREDICATE)
                                                .withQuery(Predicates.alwaysTrue()).withMaxItems(10).build()
                                        )
                                        2 -> assertThat(query("person", listOf(user))).containsExactly(user)
                                        else -> {
                                            val comment = recordsService.create(
                                                "comment",
                                                mapOf("record" to AuthorityType.PERSON.getRef(user), "text" to "Concurrent cache regression")
                                            )
                                            recordsService.delete(comment)
                                        }
                                    }
                                }
                            }
                            completed.incrementAndGet()
                        }
                    }
                )
            }
            val changes = executor.submit(
                Callable {
                    check(start.await(5, TimeUnit.SECONDS))
                    while (System.nanoTime() < deadline) {
                        val private = invalidations.incrementAndGet() % 2 == 0
                        AuthContext.runAsSystem { recordsService.mutateAtt(group, "privateGroup", private) }
                        Thread.sleep(1000)
                    }
                }
            )
            start.countDown()
            requests.forEach { it.get(seconds + 45, TimeUnit.SECONDS) }
            changes.get(45, TimeUnit.SECONDS)
            assertThat(completed.get()).isGreaterThan(40)
            assertThat(invalidations.get()).isGreaterThan(1)
            AuthContext.runAs("load-user-1", listOf(AuthRole.USER, "GROUP_load-private")) {
                assertThat(query("person", listOf("load-user-1"))).containsExactly("load-user-1")
            }
            AuthContext.runAs("load-admin", listOf(AuthRole.ADMIN)) {
                assertThat(query("person", listOf("load-user-1"))).containsExactly("load-user-1")
            }
            await().atMost(Duration.ofSeconds(10)).untilAsserted {
                dataSource.connection.use { connection ->
                    connection.createStatement().use { stmt ->
                        stmt.executeQuery(
                            "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() " +
                                "AND state = 'idle in transaction' AND xact_start < now() - interval '5 seconds'"
                        ).use { rows ->
                            check(rows.next())
                            assertThat(rows.getInt(1)).isZero()
                        }
                    }
                }
            }
            println("COREDEV-660 mixed Records load: clients=40 duration=${seconds}s completed=${completed.get()} groupChanges=${invalidations.get()}")
        } finally {
            start.countDown()
            executor.shutdownNow()
            assertThat(executor.awaitTermination(35, TimeUnit.SECONDS)).isTrue()
        }
    }

    private fun query(source: String, ids: List<String>): List<String> {
        return recordsService.query(
            RecordsQuery.create().withSourceId(source)
                .withLanguage(PredicateService.LANGUAGE_PREDICATE)
                .withQuery(Predicates.inVals("id", ids)).withMaxItems(100).build()
        ).getRecords().map { it.getLocalId() }
    }
}
