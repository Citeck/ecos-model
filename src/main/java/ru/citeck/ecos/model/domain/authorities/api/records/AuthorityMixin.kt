package ru.citeck.ecos.model.domain.authorities.api.records

import ru.citeck.ecos.model.domain.authorities.constant.AuthorityConstants
import ru.citeck.ecos.model.domain.authorities.service.AuthorityService
import ru.citeck.ecos.model.domain.authsync.service.AuthorityType
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.value.AttValueCtx
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.records3.record.mixin.AttMixin

class AuthorityMixin(
    private val recordsService: RecordsService,
    private val authorityService: AuthorityService,
    private val authorityType: AuthorityType
) : AttMixin {

    companion object {

        private val providedAtts = listOf(
            AuthorityConstants.ATT_AUTHORITY_GROUPS_FULL,
            AuthorityConstants.ATT_AUTHORITY_NAME,
            AuthorityConstants.ATT_CONTAINED_USERS
        )

        private const val ATT_AUTHORITY_GROUPS_FULL_LOCAL_ID = "${AuthorityConstants.ATT_AUTHORITY_GROUPS}[]?localId"
    }

    override fun getAtt(path: String, value: AttValueCtx): Any? {
        return when (path) {
            AuthorityConstants.ATT_AUTHORITY_NAME -> authorityType.authorityPrefix + value.getLocalId()
            AuthorityConstants.ATT_AUTHORITY_GROUPS_FULL -> {
                val parentGroups = value.getAtt(ATT_AUTHORITY_GROUPS_FULL_LOCAL_ID).asStrList()
                return authorityService.getExpandedGroups(parentGroups, true).map {
                    AuthorityType.GROUP.getRef(it)
                }
            }
            AuthorityConstants.ATT_CONTAINED_USERS -> {
                val ref = value.getRef()
                if (!ref.sourceId.startsWith("authority-group")) {
                    emptyList()
                } else {
                    val groupRef = AuthorityType.GROUP.getRef(ref.getLocalId()).toString()
                    recordsService.query(
                        RecordsQuery.create {
                            withSourceId(AuthorityType.PERSON.sourceId)
                            withQuery(Predicates.contains(AuthorityConstants.ATT_AUTHORITY_GROUPS_FULL, groupRef))
                            withMaxItems(1000)
                        }
                    ).getRecords()
                }
            }
            else -> null
        }
    }

    override fun getProvidedAtts() = providedAtts
}
