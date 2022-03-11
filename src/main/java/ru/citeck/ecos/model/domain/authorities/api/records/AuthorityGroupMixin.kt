package ru.citeck.ecos.model.domain.authorities.api.records

import ru.citeck.ecos.model.domain.authorities.constant.AuthorityGroupConstants
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.atts.value.AttValueCtx
import ru.citeck.ecos.records3.record.mixin.AttMixin

class AuthorityGroupMixin : AttMixin {

    companion object {
        const val ATT_GROUP_TYPE = "groupType"
        const val ATT_GROUP_SUB_TYPE = "groupSubType"

        const val GROUP_TYPE_BRANCH = "branch"
        const val GROUP_TYPE_ROLE = "role"
        const val GROUP_TYPE_GROUP = "group"

        private val PROVIDED_ATTS = listOf(ATT_GROUP_SUB_TYPE, ATT_GROUP_TYPE)
    }

    override fun getAtt(path: String, value: AttValueCtx): Any? {
        return when (path) {
            ATT_GROUP_TYPE -> {
                val atts = value.getAtts(GroupAtts::class.java)
                if (!atts.branchType.isNullOrBlank()) {
                    GROUP_TYPE_BRANCH
                } else if (!atts.roleType.isNullOrBlank()) {
                    GROUP_TYPE_ROLE
                } else {
                    GROUP_TYPE_GROUP
                }
            }
            ATT_GROUP_SUB_TYPE -> {
                val atts = value.getAtts(GroupAtts::class.java)
                if (!atts.branchType.isNullOrBlank()) {
                    atts.branchType
                } else if (!atts.roleType.isNullOrBlank()) {
                    atts.roleType
                } else {
                    null
                }
            }
            else -> null
        }
    }

    override fun getProvidedAtts() = PROVIDED_ATTS

    class GroupAtts(
        @AttName(AuthorityGroupConstants.ATT_ROLE_TYPE)
        val roleType: String?,
        @AttName(AuthorityGroupConstants.ATT_BRANCH_TYPE)
        val branchType: String?
    )
}
