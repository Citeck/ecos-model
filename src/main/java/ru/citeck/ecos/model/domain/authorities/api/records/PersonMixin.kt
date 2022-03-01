package ru.citeck.ecos.model.domain.authorities.api.records

import ru.citeck.ecos.model.domain.authorities.constant.PersonConstants
import ru.citeck.ecos.model.domain.authorities.service.AuthorityService
import ru.citeck.ecos.model.domain.authsync.service.AuthorityType
import ru.citeck.ecos.records3.record.atts.schema.ScalarType
import ru.citeck.ecos.records3.record.atts.value.AttValue
import ru.citeck.ecos.records3.record.atts.value.AttValueCtx
import ru.citeck.ecos.records3.record.mixin.AttMixin
import java.time.Instant
import java.util.concurrent.TimeUnit

class PersonMixin(
    private val authorityService: AuthorityService
) : AttMixin {

    companion object {

        private val providedAtts = listOf(
            PersonConstants.ATT_AUTHORITIES,
            PersonConstants.ATT_IS_ADMIN,
            PersonConstants.ATT_IS_AUTHENTICATION_MUTABLE,
            PersonConstants.ATT_FULL_NAME,
            PersonConstants.ATT_AVATAR,
            PersonConstants.ATT_IS_MUTABLE,
            PersonConstants.ATT_INACTIVITY_DAYS,
        )
    }

    override fun getAtt(path: String, value: AttValueCtx): Any? {
        return when (path) {
            PersonConstants.ATT_AUTHORITIES -> {
                return Authorities(authorityService.getAuthoritiesForPerson(value.getLocalId()))
            }
            PersonConstants.ATT_IS_ADMIN -> authorityService.isAdmin(value.getLocalId())
            PersonConstants.ATT_IS_AUTHENTICATION_MUTABLE -> false
            PersonConstants.ATT_FULL_NAME -> value.getAtt(ScalarType.DISP.schema)
            PersonConstants.ATT_AVATAR -> {
                val hash = value.getAtt(PersonConstants.ATT_PHOTO + ".sha256").asText()
                if (hash.isNotBlank()) {
                    Avatar(value.getLocalId(), hash)
                } else {
                    null
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
