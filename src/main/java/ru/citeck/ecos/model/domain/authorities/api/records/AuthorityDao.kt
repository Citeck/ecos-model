package ru.citeck.ecos.model.domain.authorities.api.records

import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.context.lib.auth.AuthRole
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.model.domain.workspace.dto.WorkspaceMemberRole
import ru.citeck.ecos.records2.predicate.model.Predicate
import ru.citeck.ecos.records2.predicate.model.Predicates
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery

@Component
class AuthorityDao :
    AbstractRecordsDao(),
    RecordsQueryDao,
    RecordAttsDao {

    companion object {
        const val SRC_ID = "authority"
    }

    private var defaultRoles = listOf(
        RoleValue(
            MLText(
                I18nContext.RUSSIAN to "Администратор системы",
                I18nContext.ENGLISH to "System Administrator"
            ),
            AuthRole.ADMIN
        ),
        RoleValue(
            MLText(
                I18nContext.RUSSIAN to "Участник рабочего пространства",
                I18nContext.ENGLISH to "Workspace Member"
            ),
            AuthRole.PREFIX + "WS_" + WorkspaceMemberRole.USER
        ),
        RoleValue(
            MLText(
                I18nContext.RUSSIAN to "Менеджер рабочего пространства",
                I18nContext.ENGLISH to "Workspace Manager"
            ),
            AuthRole.PREFIX + "WS_" + WorkspaceMemberRole.MANAGER
        )
    )

    override fun getRecordAtts(recordId: String): Any? {
        if (recordId.startsWith(AuthRole.PREFIX)) {
            return getRoles().find { it.value == recordId }
        }
        return null
    }

    override fun queryRecords(recsQuery: RecordsQuery): Any? {
        val query = recsQuery.getQuery(Query::class.java)
        val queryRes = ArrayList<Any>()
        if (query.types.contains(QueryAuthType.ROLE)) {
            queryRes.addAll(getRoles())
        }
        return predicateService.filterAndSort(
            queryRes,
            recsQuery.getPredicate(),
            recsQuery.sortBy,
            recsQuery.page.skipCount,
            recsQuery.page.maxItems
        )
    }

    private fun getRoles(): List<RoleValue> {
        return defaultRoles
    }

    override fun getId(): String {
        return SRC_ID
    }

    private class Query(
        val scope: String = "",
        val types: List<QueryAuthType> = emptyList(),
        val predicate: Predicate = Predicates.alwaysTrue()
    )

    enum class QueryAuthType {
        PERSON,
        GROUP,
        ROLE
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
