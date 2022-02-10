package ru.citeck.ecos.model.domain.authorities.api.records

import ru.citeck.ecos.model.domain.authorities.AuthorityConstants
import ru.citeck.ecos.model.domain.authorities.service.AuthorityService
import ru.citeck.ecos.model.domain.authsync.service.AuthorityType
import ru.citeck.ecos.records3.record.atts.value.AttValueCtx
import ru.citeck.ecos.records3.record.mixin.AttMixin

class AuthorityMixin(
    private val authorityService: AuthorityService
) : AttMixin {

    companion object {

        private val providedAtts = listOf(
            AuthorityConstants.ATT_AUTHORITY_GROUPS_FULL
        )
    }

    override fun getAtt(path: String, value: AttValueCtx): Any? {
        if (path != AuthorityConstants.ATT_AUTHORITY_GROUPS_FULL) {
            return null
        }
        val parentGroups = value.getAtt("${AuthorityConstants.ATT_AUTHORITY_GROUPS}[]?localId").asStrList()
        return authorityService.getExpandedGroups(parentGroups, true).map {
            AuthorityType.GROUP.getRef(it)
        }
    }

    override fun getProvidedAtts() = providedAtts
}
