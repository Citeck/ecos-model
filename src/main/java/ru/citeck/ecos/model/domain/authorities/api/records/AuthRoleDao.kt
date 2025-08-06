package ru.citeck.ecos.model.domain.authorities.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.model.domain.workspace.desc.WorkspaceMemberDesc
import ru.citeck.ecos.model.lib.ModelServiceFactory
import ru.citeck.ecos.model.lib.attributes.computed.ComputedAttsService
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.model.type.registry.EcosTypesRegistry
import java.time.Instant

@Component
class AuthRoleDao(
    private val typesRegistry: EcosTypesRegistry,
    modelServices: ModelServiceFactory
) : RecordsQueryDao, AbstractRecordsDao() {

    companion object {
        const val SRC_ID = "auth-role"
    }

    private var defaultRoles = listOf(
        RoleValue(
            MLText(
                I18nContext.RUSSIAN to "Администратор системы",
                I18nContext.ENGLISH to "System Administrator"
            ),
            AuthRole.ADMIN
        )
    )

    private var computedAttsService: ComputedAttsService = modelServices.computedAttsService

    private var wsMemberTypeChangedAt = Instant.EPOCH
    private var cachedWsRoles = emptyList<RoleValue>()

    override fun queryRecords(recsQuery: RecordsQuery): Any? {

        val queryRes = ArrayList<RoleValue>(defaultRoles)

        val memberType = typesRegistry.getValueWithMeta(WorkspaceMemberDesc.TYPE_ID)

        if (memberType != null && wsMemberTypeChangedAt.isBefore(memberType.meta.modified)) {

            val memberRoleAtt = memberType.entity.model.attributes
                .find { it.id == WorkspaceMemberDesc.ATT_MEMBER_ROLE }

            if (memberRoleAtt != null) {
                cachedWsRoles = computedAttsService.getAttOptions(EntityRef.EMPTY, memberRoleAtt.config).map {
                    RoleValue(it.label, "ROLE_WS_" + it.value)
                }
            }
            wsMemberTypeChangedAt = memberType.meta.modified
        }
        queryRes.addAll(cachedWsRoles)

        return predicateService.filterAndSort(
            queryRes,
            recsQuery.getPredicate(),
            recsQuery.sortBy,
            recsQuery.page.skipCount,
            recsQuery.page.maxItems
        )
    }

    override fun getId(): String {
        return SRC_ID
    }

    @Suppress("unused")
    private class RoleValue(
        @AttName("?disp")
        val name: MLText,
        @AttName("?str")
        val value: String
    ) {
        fun getId(): String {
            return value
        }
    }
}
