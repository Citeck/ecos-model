package ru.citeck.ecos.model.domain.authsync.service

import ru.citeck.ecos.records2.RecordRef

enum class AuthorityType(val sourceId: String) {

    PERSON("person"),
    GROUP("authority-group");

    fun getRef(id: String): RecordRef {
        return RecordRef.create("emodel", sourceId, id)
    }
}
