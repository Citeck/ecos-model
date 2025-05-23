package ru.citeck.ecos.model.domain.authorities

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityConstants
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityGroupConstants
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import javax.sql.DataSource

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosModelApp::class])
class AuthoritiesTestBase {

    companion object {
        val DEFAULT_AUTHORITIES = setOf(
            *AuthorityGroupConstants.DEFAULT_GROUPS.toTypedArray(),
            "admin"
        )
    }

    @Autowired
    lateinit var recordsService: RecordsService

    @Autowired
    lateinit var dataSource: DataSource

    private var authAware: Boolean = false

    /**
     * @param authAware false - all create/update operations will be performed as system
     */
    fun initTest(authAware: Boolean = false) {
        this.authAware = authAware
    }

    fun deleteAll(type: AuthorityType) {
        val allRecords = recordsService.query(
            RecordsQuery.create {
                withSourceId(type.sourceId + "-repo")
                withQuery(
                    Predicates.not(
                        Predicates.inVals(ScalarType.LOCAL_ID.mirrorAtt, DEFAULT_AUTHORITIES)
                    )
                )
                withMaxItems(Int.MAX_VALUE)
            }
        ).getRecords()
        recordsService.delete(allRecords)
    }

    @BeforeEach
    fun beforeEach() {
        deleteAll(AuthorityType.GROUP)
        deleteAll(AuthorityType.PERSON)
    }

    fun assertStrListAtt(ref: EntityRef, att: String, expected: List<String>) {
        val list = recordsService.getAtt(ref, att).toStrList()
        Assertions.assertThat(list).containsExactlyInAnyOrderElementsOf(expected)
    }

    fun createPerson(id: String, vararg props: Pair<String, Any?>): EntityRef {
        return createAuthority(id, AuthorityType.PERSON, props.toMap())
    }

    fun createGroup(id: String, vararg props: Pair<String, Any?>): EntityRef {
        return createAuthority(id, AuthorityType.GROUP, props.toMap())
    }

    fun createAuthority(id: String, type: AuthorityType, props: Map<String, Any?>): EntityRef {
        val authorityAtts = ObjectData.create().set("id", id)
        props.forEach {
            authorityAtts[it.key] = it.value
        }
        return runInAuthCtx {
            recordsService.create(type.sourceId, authorityAtts)
                .withAppName(EcosModelApp.NAME)
                .withSourceId(type.sourceId)
        }
    }

    fun addUserToGroup(userId: String, groupId: String) {
        addAuthorityToGroup(AuthorityType.PERSON.getRef(userId), AuthorityType.GROUP.getRef(groupId))
    }

    fun addAuthorityToGroup(authorityRef: EntityRef, targetGroup: EntityRef) {
        runInAuthCtx {
            recordsService.mutateAtt(
                authorityRef,
                "att_add_${AuthorityConstants.ATT_AUTHORITY_GROUPS}",
                targetGroup
            )
        }
    }

    fun removeUserFromGroup(userId: String, groupId: String) {
        runInAuthCtx {
            recordsService.mutateAtt(
                AuthorityType.PERSON.getRef(userId),
                "att_rem_${AuthorityConstants.ATT_AUTHORITY_GROUPS}",
                AuthorityType.GROUP.getRef(groupId)
            )
        }
    }

    fun setUserGroups(userId: String, groups: List<String>) {
        setAuthorityGroups(AuthorityType.PERSON.getRef(userId), groups.map { AuthorityType.GROUP.getRef(it) })
    }

    fun setAuthorityGroups(authorityRef: EntityRef, groups: List<EntityRef>) {
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
