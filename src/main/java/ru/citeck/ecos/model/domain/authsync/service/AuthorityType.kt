package ru.citeck.ecos.model.domain.authsync.service

import ru.citeck.ecos.context.lib.auth.AuthGroup
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.records2.RecordRef

enum class AuthorityType(val sourceId: String, val authorityPrefix: String) {

    PERSON("person", ""),
    GROUP("authority-group", AuthGroup.PREFIX);

    fun getRef(id: String): RecordRef {
        return RecordRef.create(EcosModelApp.NAME, sourceId, id)
    }
}
