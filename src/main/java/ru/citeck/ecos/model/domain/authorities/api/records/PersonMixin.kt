package ru.citeck.ecos.model.domain.authorities.api.records

import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.context.lib.auth.AuthGroup
import ru.citeck.ecos.context.lib.i18n.I18nContext
import ru.citeck.ecos.model.domain.authorities.constant.PersonConstants
import ru.citeck.ecos.model.domain.authorities.service.AuthorityService
import ru.citeck.ecos.model.lib.authorities.AuthorityType
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.atts.value.AttValue
import ru.citeck.ecos.records3.record.atts.value.AttValueCtx
import ru.citeck.ecos.records3.record.mixin.AttMixin
import java.time.Instant
import java.util.concurrent.TimeUnit

class PersonMixin(
    private val recordsService: RecordsService,
    private val authorityService: AuthorityService
) : AttMixin {

    companion object {

        private val NON_DELEGATABLE_AUTHORITIES = setOf(
            "GROUP__orgstruct_home_",
            AuthGroup.EVERYONE
        )
        private val OWN_DELEGATABLE_AUTHORITY_NAME = MLText(
            I18nContext.ENGLISH to "Own",
            I18nContext.RUSSIAN to "Свои"
        )

        private val providedAtts = listOf(
            PersonConstants.ATT_AUTHORITIES,
            PersonConstants.ATT_IS_ADMIN,
            PersonConstants.ATT_IS_AUTHENTICATION_MUTABLE,
            PersonConstants.ATT_FULL_NAME,
            PersonConstants.ATT_AVATAR,
            PersonConstants.ATT_AVATAR_URL,
            PersonConstants.ATT_IS_MUTABLE,
            PersonConstants.ATT_INACTIVITY_DAYS,
            PersonConstants.ATT_DELEGATABLE_AUTHORITIES
        )
    }

    override fun getAtt(path: String, value: AttValueCtx): Any? {
        return when (path) {
            PersonConstants.ATT_AUTHORITIES -> {
                return Authorities(authorityService.getAuthoritiesForPerson(value.getLocalId()))
            }

            PersonConstants.ATT_DELEGATABLE_AUTHORITIES -> {

                val authorities = authorityService.getAuthoritiesForPerson(value.getLocalId())
                    .filter {
                        it.startsWith(AuthGroup.PREFIX) &&
                            !NON_DELEGATABLE_AUTHORITIES.contains(it) &&
                            !authorityService.isAdminsGroup(it)
                    }

                val displayNames = recordsService.getAtts(
                    authorities.map { AuthorityType.GROUP.getRef(it.replaceFirst(AuthGroup.PREFIX, "")) },
                    mapOf("dispName" to ScalarType.DISP.schema)
                ).map { it.getAtt("dispName").asText().ifBlank { it.getId().getLocalId() } }

                val result = ArrayList<DelegatableAuthority>(authorities.size + 1)
                result.add(DelegatableAuthority("OWN", OWN_DELEGATABLE_AUTHORITY_NAME))

                for ((idx, authName) in authorities.withIndex()) {
                    result.add(DelegatableAuthority(authName, displayNames[idx]))
                }

                return result
            }

            PersonConstants.ATT_IS_ADMIN -> authorityService.isAdmin(value.getLocalId())
            PersonConstants.ATT_IS_AUTHENTICATION_MUTABLE -> false
            PersonConstants.ATT_FULL_NAME -> value.getAtt(ScalarType.DISP.schema)
            PersonConstants.ATT_AVATAR_URL,
            PersonConstants.ATT_AVATAR -> {
                val hash = value.getAtt(PersonConstants.ATT_PHOTO + ".sha256").asText()
                val avatar = if (hash.isNotBlank()) {
                    Avatar(value.getLocalId(), hash)
                } else {
                    null
                }
                if (path == PersonConstants.ATT_AVATAR_URL) {
                    return avatar?.getUrl()
                } else {
                    return avatar
                }
            }
            PersonConstants.ATT_IS_MUTABLE -> false
            PersonConstants.ATT_INACTIVITY_DAYS -> {

                val lastActivity = value.getAtt(PersonConstants.ATT_LAST_ACTIVITY_TIME).getAs(Instant::class.java)
                if (lastActivity == null || lastActivity == Instant.EPOCH) {
                    return 0
                }
                val inactivityMs = Instant.now().toEpochMilli() - lastActivity.toEpochMilli()
                return inactivityMs / TimeUnit.DAYS.toMillis(1)
            }
            else -> null
        }
    }

    override fun getProvidedAtts(): Collection<String> {
        return providedAtts
    }

    class DelegatableAuthority(
        private val authorityName: String,
        private val displayName: Any
    ) : AttValue {

        override fun asText(): String {
            return authorityName
        }

        override fun getDisplayName(): Any {
            return displayName
        }
    }

    class Authorities(val authorities: Set<String>) : AttValue {

        override fun has(name: String): Boolean {
            return authorities.contains(name)
        }

        override fun getAtt(name: String): Any? {
            return when (name) {
                "list" -> authorities
                else -> null
            }
        }
    }

    class Avatar(private val personId: String, private val hash: String) {
        fun getUrl(): String {
            val ref = AuthorityType.PERSON.getRef(personId).toString()
            return "/gateway/emodel/api/ecosdata/image?ref=$ref&att=photo&cb=${hash.take(20)}"
        }
    }
}
