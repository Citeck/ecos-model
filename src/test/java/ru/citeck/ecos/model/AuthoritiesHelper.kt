package ru.citeck.ecos.model

import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityConstants
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.api.entity.EntityRef

@Component
class AuthoritiesHelper(
    val recordsService: RecordsService
) {
    fun addUserToGroup(userId: String, groupId: String) {
        addAuthorityToGroup(AuthorityType.PERSON.getRef(userId), AuthorityType.GROUP.getRef(groupId))
    }

    fun addAuthorityToGroup(authorityRef: EntityRef, targetGroup: EntityRef) {
        recordsService.mutateAtt(
            authorityRef,
            "att_add_${AuthorityConstants.ATT_AUTHORITY_GROUPS}",
            targetGroup
        )
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
        return recordsService.create(type.sourceId, authorityAtts)
            .withAppName(EcosModelApp.NAME)
            .withSourceId(type.sourceId)
    }
}
