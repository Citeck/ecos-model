package ru.citeck.ecos.model.domain.authorities

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.iter.IterableRecords
import ru.citeck.ecos.records3.iter.IterableRecordsConfig
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.dao.query.dto.query.SortBy
import ru.citeck.ecos.txn.lib.TxnContext
import ru.citeck.ecos.webapp.api.entity.EntityRef

/**
 * Verify the actual records schema/storage, independently from the LDAP transport fixture.
 */
class LdapGroupSnapshotTest : AuthoritiesTestBase() {

    private fun members(group: EntityRef, attribute: String = "ldapGroups"): List<EntityRef> {

        return listOf("person", "authority-group").flatMap { source ->
            val query = RecordsQuery.create {
                withSourceId("emodel/$source")
                withQuery(Predicates.eq(attribute, group))
                withSortBy(listOf(SortBy("_created", ascending = true), SortBy("id", ascending = true)))
                withMaxItems(0)
            }
            val expected = recordsService.query(query).getTotalCount()

            val result = IterableRecords(
                query.copy().withMaxItems(-1).build(),
                IterableRecordsConfig.create {
                    withPageSize(37)
                },
                recordsService
            ).map {
                it.getId()
            }

            assertThat(result.toSet()).hasSize(expected.toInt())
            assertThat(result).doesNotHaveDuplicates()
            result
        }
    }

    @Test
    fun `large LDAP snapshot and parent graph are complete beyond association preview limit`() {

        AuthContext.runAsSystem {
            val people = (1..325).map {
                createPerson("ldap-large-person-$it")
            }
            val parents = (1..325).map {
                createGroup("ldap-large-parent-$it")
            }
            val group = createGroup("ldap-large-group", "authorityGroups" to parents)

            (people + parents).forEach {
                recordsService.mutateAtt(it, "att_add_ldapGroups", group)
            }

            assertThat(members(group)).containsExactlyInAnyOrderElementsOf(people + parents)
            parents.forEach {
                assertThat(members(it, "authorityGroups")).contains(group)
            }

            assertThrows<IllegalStateException> {
                TxnContext.doInNewTxn {
                    (people.drop(5) + parents).forEach {
                        recordsService.mutateAtt(it, "att_rem_ldapGroups", group)
                    }
                    error("Rollback large snapshot replacement")
                }
            }

            assertThat(members(group)).containsExactlyInAnyOrderElementsOf(people + parents)

            (people.take(300) + parents).forEach {
                recordsService.mutateAtt(it, "att_rem_ldapGroups", group)
            }

            assertThat(members(group)).containsExactlyInAnyOrderElementsOf(people.drop(300))

            // A member with more than 150 provenance links must support delta updates too.
            recordsService.mutateAtt(people.last(), "att_add_ldapGroups", parents)
            recordsService.mutateAtt(people.last(), "att_rem_ldapGroups", parents.drop(1))

            assertThat(members(parents.first())).contains(people.last())
            assertThat(members(parents.last())).doesNotContain(people.last())
            assertThat(recordsService.getAtt(people.last(), "authorityGroups[]?id").asList(EntityRef::class.java))
                .doesNotContain(group)
        }
    }

    @Test
    fun `LDAP snapshot holds persons and nested groups without granting effective membership`() {

        AuthContext.runAsSystem {
            val person = createPerson("ldap-snapshot-person")
            val child = createGroup("ldap-snapshot-child")
            val group = createGroup(
                "ldap-snapshot-parent",
                "ldapDn" to "cn=parent,dc=example",
                "ldapPresent" to true
            )

            listOf(person, child).forEach {
                recordsService.mutateAtt(it, "att_add_ldapGroups", group)
            }

            assertThat(members(group)).containsExactlyInAnyOrder(person, child)
            assertThat(recordsService.getAtt(group, "ldapDn").asText()).isEqualTo("cn=parent,dc=example")
            assertThat(recordsService.getAtt(group, "ldapPresent?bool").asBoolean()).isTrue()
            listOf(person, child).forEach {
                assertThat(recordsService.getAtt(it, "authorityGroups[]?id").asList(EntityRef::class.java))
                    .doesNotContain(group)
            }
        }
    }

    @Test
    fun `membership and LDAP snapshot roll back together and retirement keeps identity`() {

        AuthContext.runAsSystem {
            val person = createPerson("ldap-snapshot-person")
            val group = createGroup(
                "ldap-snapshot-parent",
                "ldapDn" to "cn=parent,dc=example",
                "ldapPresent" to true
            )

            recordsService.mutateAtt(person, "att_add_ldapGroups", group)

            assertThrows<IllegalStateException> {
                TxnContext.doInNewTxn {
                    recordsService.mutateAtt(person, "att_add_authorityGroups", group)
                    recordsService.mutateAtt(person, "att_rem_ldapGroups", group)
                    error("Simulated LDAP snapshot transaction failure")
                }
            }

            assertThat(recordsService.getAtt(person, "authorityGroups[]?id").asList(EntityRef::class.java)).doesNotContain(group)
            assertThat(members(group)).containsExactly(person)

            TxnContext.doInNewTxn {
                recordsService.mutateAtt(person, "att_rem_ldapGroups", group)
                recordsService.mutateAtt(group, "ldapPresent", value = false)
            }

            assertThat(members(group)).isEmpty()
            assertThat(recordsService.getAtt(group, "ldapPresent?bool").asBoolean()).isFalse()
            assertThat(recordsService.getAtt(group, "ldapDn").asText()).isEqualTo("cn=parent,dc=example")
        }
    }
}
