package ru.citeck.ecos.model.domain.authsync.service

enum class AuthorityType(val sourceId: String) {
    PERSON("person"),
    GROUP("authority-group")
}
