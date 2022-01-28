package ru.citeck.ecos.model.domain.authorities.config

import ru.citeck.ecos.model.domain.authsync.service.AuthorityType
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.records3.record.atts.value.AttValue
import ru.citeck.ecos.records3.record.atts.value.AttValueCtx
import ru.citeck.ecos.records3.record.mixin.AttMixin

class PersonsMixin(val recordsService: RecordsService) : AttMixin {

    companion object {

        private const val ATT_AUTHORITIES = "authorities"

        private const val ATT_AUTHORITY_GROUPS = "authorityGroups[]?localId"

        private val providedAtts = listOf(
            ATT_AUTHORITIES
        )
    }

    override fun getAtt(path: String, value: AttValueCtx): Any? {
        return when (path) {
            ATT_AUTHORITIES -> {

                val authoritiesList = ArrayList<String>()
                authoritiesList.add(value.getLocalId())
                authoritiesList.add("GROUP_EVERYONE")

                val groups = value.getAtt(ATT_AUTHORITY_GROUPS).asStrList()
                val authoritiesSet = HashSet<String>(groups)

                while (groups.isNotEmpty()) {

                    authoritiesList.addAll(groups.map { "GROUP_$it" })

                    val groupsToCheck = ArrayList<String>()
                    for (group in groups) {
                        val groupRef = RecordRef.create(AuthorityType.GROUP.sourceId, group)
                        val groupGroups = recordsService.getAtt(groupRef, ATT_AUTHORITY_GROUPS).asStrList()
                        groupGroups.forEach {
                            if (authoritiesSet.add(it)) {
                                groupsToCheck.add(it)
                            }
                        }
                    }
                    groups.clear()
                    groups.addAll(groupsToCheck)
                }

                return Authorities(authoritiesList)
            }
            else -> null
        }
    }

    override fun getProvidedAtts(): Collection<String> {
        return providedAtts
    }

    class Authorities(val authorities: List<String>) : AttValue {

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
