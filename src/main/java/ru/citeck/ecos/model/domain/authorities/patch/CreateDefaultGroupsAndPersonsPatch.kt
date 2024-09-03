package ru.citeck.ecos.model.domain.authorities.patch

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityConstants
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityGroupConstants
import ru.citeck.ecos.model.domain.authorities.constant.PersonConstants
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.api.entity.EntityRef
import ru.citeck.ecos.webapp.lib.patch.annotaion.EcosPatch
import java.util.concurrent.Callable

@Component
@EcosPatch("default-authorities", "2022-06-29T00:00:03Z")
class CreateDefaultGroupsAndPersonsPatch(
    val recordsService: RecordsService
) : Callable<List<String>> {

    companion object {

        private const val ORGSTRUCT_HOME_GROUP = "_orgstruct_home_"

        private val DEFAULT_GROUPS = listOf(
            SystemGroupInfo(
                AuthorityGroupConstants.EVERYONE_GROUP,
                MLText(
                    I18nContext.ENGLISH to "All persons in system",
                    I18nContext.RUSSIAN to "Все пользователи в системе"
                )
            ),
            SystemGroupInfo(
                AuthorityGroupConstants.ADMIN_GROUP,
                MLText(
                    I18nContext.ENGLISH to "ECOS Administrators",
                    I18nContext.RUSSIAN to "Администраторы ECOS"
                )
            ),
            SystemGroupInfo(
                ORGSTRUCT_HOME_GROUP,
                MLText(
                    I18nContext.ENGLISH to "Orgstruct",
                    I18nContext.RUSSIAN to "Оргструктура"
                )
            ),
            SystemGroupInfo(
                AuthorityGroupConstants.USERS_PROFILE_ADMIN_GROUP,
                MLText(
                    I18nContext.ENGLISH to "Users profile admins",
                    I18nContext.RUSSIAN to "Администраторы профиля пользователей"
                )
            ),
            SystemGroupInfo(
                AuthorityGroupConstants.MANAGED_GROUPS_GROUP,
                MLText(
                    I18nContext.ENGLISH to "Managed groups",
                    I18nContext.RUSSIAN to "Группы управляемые менеджером"
                )
            ),
            SystemGroupInfo(
                AuthorityGroupConstants.GROUPS_MANAGERS_GROUP,
                MLText(
                    I18nContext.ENGLISH to "Groups managers",
                    I18nContext.RUSSIAN to "Менеджеры групп"
                )
            ),
            SystemGroupInfo(
                AuthorityGroupConstants.EXTERNAL_USERS_GROUP,
                MLText(
                    I18nContext.ENGLISH to "External users",
                    I18nContext.RUSSIAN to "Внешние пользователи"
                )
            ),
            SystemGroupInfo(
                AuthorityGroupConstants.UNIFIED_PRIVATE_GROUP,
                MLText(
                    I18nContext.ENGLISH to "Unified private group",
                    I18nContext.RUSSIAN to "Единая приватная группа"
                )
            )
        )

        private val DEFAULT_USERS = listOf(
            SystemUserInfo(
                "admin",
                "",
                "",
                "admin@admin.ru",
                listOf(AuthorityGroupConstants.ADMIN_GROUP, ORGSTRUCT_HOME_GROUP)
            )
        )

        private val log = KotlinLogging.logger {}
    }

    override fun call(): List<String> {
        val messages = mutableListOf<String>()
        val logMsg: (String) -> Unit = {
            messages.add(it)
            log.info { it }
        }
        DEFAULT_GROUPS.forEach { groupInfo ->
            createIfNotExists(
                AuthorityType.GROUP,
                groupInfo.id,
                mapOf(
                    AuthorityGroupConstants.ATT_NAME to groupInfo.name,
                    AuthorityConstants.ATT_AUTHORITY_GROUPS to groupInfo.groups.map { AuthorityType.GROUP.getRef(it) }
                ),
                logMsg
            )
        }
        DEFAULT_USERS.forEach { user ->
            createIfNotExists(
                AuthorityType.PERSON,
                user.id,
                mapOf(
                    PersonConstants.ATT_FIRST_NAME to user.firstName,
                    PersonConstants.ATT_LAST_NAME to user.lastName,
                    PersonConstants.ATT_EMAIL to user.email,
                    AuthorityConstants.ATT_AUTHORITY_GROUPS to user.groups.map { AuthorityType.GROUP.getRef(it) }
                ),
                logMsg
            )
        }
        return messages
    }

    private fun createIfNotExists(type: AuthorityType, id: String, atts: Map<String, Any>, logMsg: (String) -> Unit) {

        val isNotExists = recordsService.getAtt(
            type.getRef(id),
            RecordConstants.ATT_NOT_EXISTS + "?bool"
        ).asBoolean()

        if (isNotExists) {
            logMsg("authority '$id' with type '$type' is not found. Create it")
            val attsToCreate = ObjectData.create(atts)
            attsToCreate["id"] = id
            recordsService.create(type.sourceId, attsToCreate)
        } else {
            logMsg("authority '$id' with type '$type' found")
            @Suppress("UNCHECKED_CAST")
            val expectedGroups =
                atts[AuthorityConstants.ATT_AUTHORITY_GROUPS] as? Collection<EntityRef> ?: emptyList()
            if (expectedGroups.isEmpty()) {
                logMsg("nothing to do with $id")
            } else {
                val authorityRef = type.getRef(id)
                val actualGroups = recordsService.getAtt(
                    authorityRef,
                    AuthorityConstants.ATT_AUTHORITY_GROUPS + "[]?id"
                ).asList(EntityRef::class.java)
                expectedGroups.forEach {
                    if (!actualGroups.contains(it)) {
                        logMsg("Add group for authority '$id'. Group: $it")
                        recordsService.mutateAtt(
                            authorityRef,
                            "att_add_${AuthorityConstants.ATT_AUTHORITY_GROUPS}",
                            it
                        )
                    }
                }
            }
        }
    }

    data class SystemGroupInfo(
        val id: String,
        val name: MLText,
        val groups: List<String> = emptyList()
    )

    data class SystemUserInfo(
        val id: String,
        val firstName: String,
        val lastName: String,
        val email: String,
        val groups: List<String> = emptyList()
    )
}
