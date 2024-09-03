package ru.citeck.ecos.model.domain.activity.config

enum class ActivityStatus {
    PLANNED,
    COMPLETED,
    EXPIRED,
    CANCELED;

    val id: String = name.lowercase()
}
