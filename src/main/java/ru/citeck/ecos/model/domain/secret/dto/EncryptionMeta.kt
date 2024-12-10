package ru.citeck.ecos.model.domain.secret.dto

data class EncryptionMeta(
    val algorithm: String,
    val ivSize: Int,
    val tagSize: Int
)
