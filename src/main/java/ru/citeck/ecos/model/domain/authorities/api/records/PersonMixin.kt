package ru.citeck.ecos.model.domain.authorities.api.records

import ru.citeck.ecos.model.domain.authorities.service.AuthorityService
import ru.citeck.ecos.records3.record.atts.value.AttValue
import ru.citeck.ecos.records3.record.atts.value.AttValueCtx
import ru.citeck.ecos.records3.record.mixin.AttMixin

class PersonMixin(
    private val authorityService: AuthorityService
) : AttMixin {

    companion object {

        private const val ATT_AUTHORITIES = "authorities"

        private val providedAtts = listOf(
            ATT_AUTHORITIES
        )
    }

    override fun getAtt(path: String, value: AttValueCtx): Any? {
        return when (path) {
            ATT_AUTHORITIES -> {
                return Authorities(authorityService.getAuthoritiesForPerson(value.getLocalId()))
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
}
