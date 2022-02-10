package ru.citeck.ecos.model.domain.authorities

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit.jupiter.SpringExtension
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.model.domain.authsync.service.AuthorityType
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records2.predicate.model.VoidPredicate
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import javax.annotation.PostConstruct

@ExtendWith(SpringExtension::class)
@SpringBootTest(classes = [EcosModelApp::class])
class AuthoritiesTestBase {

    @Autowired
    lateinit var recordsService: RecordsService
    @Autowired
    lateinit var dataSource: HikariDataSource

    private var authAware: Boolean = false

    /**
     * @param authAware false - all create/update operations will be performed as system
     */
    fun initTest(authAware: Boolean = false) {
        this.authAware = authAware
    }

    @PostConstruct
    fun postConstruct() {
        println("JDBC URL: " + dataSource.jdbcUrl)
    }

    fun deleteAll(type: AuthorityType) {
        val allRecords = recordsService.query(RecordsQuery.create {
            withSourceId(type.sourceId + "-repo")
            withQuery(VoidPredicate.INSTANCE)
            withMaxItems(Int.MAX_VALUE)
        }).getRecords()
        recordsService.delete(allRecords)
    }

    @BeforeEach
    fun beforeEach() {
        deleteAll(AuthorityType.GROUP)
        deleteAll(AuthorityType.PERSON)
    }

    fun assertStrListAtt(ref: RecordRef, att: String, expected: List<String>) {
        val list = recordsService.getAtt(ref, att).toStrList()
        Assertions.assertThat(list).containsExactlyInAnyOrderElementsOf(expected)
    }

    fun createPerson(id: String, vararg props: Pair<String, Any?>): RecordRef {
        return createAuthority(id, AuthorityType.PERSON, props.toMap())
    }

    fun createGroup(id: String, vararg props: Pair<String, Any?>): RecordRef {
        return createAuthority(id, AuthorityType.GROUP, props.toMap())
    }

    fun createAuthority(id: String, type: AuthorityType, props: Map<String, Any?>): RecordRef {
        val authorityAtts = ObjectData.create().set("id", id)
        props.forEach {
            authorityAtts.set(it.key, it.value)
        }
        return runInAuthCtx {
            recordsService.create(type.sourceId, authorityAtts)
                .withAppName("emodel")
                .withSourceId(type.sourceId)
        }
    }

    fun addAuthorityToGroup(authorityRef: RecordRef, targetGroup: RecordRef) {
        runInAuthCtx {
            recordsService.mutateAtt(
                authorityRef,
                "att_add_${AuthorityConstants.ATT_AUTHORITY_GROUPS}",
                targetGroup
            )
        }
    }

    fun setAuthorityGroups(authorityRef: RecordRef, groups: List<RecordRef>) {
        runInAuthCtx {
            recordsService.mutateAtt(
                authorityRef,
                AuthorityConstants.ATT_AUTHORITY_GROUPS,
                groups
            )
        }
    }

    private fun <T> runInAuthCtx(action: () -> T): T {
        if (authAware) {
            return action.invoke()
        }
        return AuthContext.runAsSystem {
            action.invoke()
        }
    }
}
