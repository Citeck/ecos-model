package ru.citeck.ecos.model.domain.authorities.patch

import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityConstants
import ru.citeck.ecos.model.domain.authorities.constant.AuthorityGroupConstants
import ru.citeck.ecos.model.domain.authorities.constant.PersonConstants
import ru.citeck.ecos.model.domain.authsync.service.AuthorityType
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.webapp.lib.patch.annotaion.EcosPatch
import java.util.concurrent.Callable

@Component
@EcosPatch("default-authorities", "2022-06-29T00:00:00Z")
class CreateDefaultGroupsAndPersonsPatch(
    val recordsService: RecordsService
) : Callable<List<String>> {

    companion object {

        val DEFAULT_GROUPS = listOf(
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
            )
        )

        val DEFAULT_USERS = listOf(
            SystemUserInfo(
                "admin",
                "",
                "",
                "admin@admin.ru",
                listOf(AuthorityGroupConstants.ADMIN_GROUP)
            )
        )
        val log = KotlinLogging.logger {}
    }

    override fun call(): List<String> {
        val messages = mutableListOf<String>()
        val logMsg: (String) -> Unit = {
            messages.add(it)
            log.info { it }
        }
        DEFAULT_GROUPS.forEach {
            createIfNotExists(
                AuthorityType.GROUP, it.id,
                mapOf(
                    AuthorityGroupConstants.ATT_NAME to it.name
                ),
                logMsg
            )
        }
        DEFAULT_USERS.forEach {
            createIfNotExists(
                AuthorityType.PERSON, it.id,
                mapOf(
                    PersonConstants.ATT_FIRST_NAME to it.firstName,
                    PersonConstants.ATT_LAST_NAME to it.lastName,
                    PersonConstants.ATT_EMAIL to it.email,
                    AuthorityConstants.ATT_AUTHORITY_GROUPS to it.groups.map { AuthorityType.GROUP.getRef(it) },
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
            logMsg("authority '$id' with type '$type' found. Skip it")
        }
    }

    data class SystemGroupInfo(
        val id: String,
        val name: MLText
    )

    data class SystemUserInfo(
        val id: String,
        val firstName: String,
        val lastName: String,
        val email: String,
        val groups: List<String> = emptyList()
    )
}
