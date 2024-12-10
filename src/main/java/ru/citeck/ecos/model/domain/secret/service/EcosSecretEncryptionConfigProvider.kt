package ru.citeck.ecos.model.domain.secret.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.util.*
import javax.annotation.PostConstruct
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

interface EcosSecretEncryptionConfigProvider {

    fun getCurrentSecretKey(): SecretKey

    fun getCurrentSecretKeyBase64(): String

    fun getCurrentAlgorithm(): String

    fun getCurrentIvSize(): Int

    fun getCurrentTagSize(): Int

    fun getPreviousSecretKey(): SecretKey?

    fun getPreviousSecretKeyBase64(): String?
}

@Component
class EncryptionConfigFromProperties(
    private val environment: Environment,

    @Value("\${ecos.secret.encryption.previous.key}")
    private val previousBase64Key: String,

    @Value("\${ecos.secret.encryption.current.key}")
    private val currentBase64Key: String,

    @Value("\${ecos.secret.encryption.current.algorithm}")
    private val currentAlgorithm: String,

    @Value("\${ecos.secret.encryption.current.ivSize}")
    private val currentIvSize: Int,

    @Value("\${ecos.secret.encryption.current.tagSize}")
    private val currentTagSize: Int
) : EcosSecretEncryptionConfigProvider {

    companion object {
        const val DEFAULT_ENCRYPTION_KEY = "Cz6ruLL9XkNjn4vaU0/MDQ=="
        const val ENCRYPTION_KEY_ALGORITHM = "AES"

        private val log = KotlinLogging.logger { }

        private const val PROD_PROFILE = "prod"
        private const val TEST_PROFILE = "test"

        private const val SECURITY_MSG = "***** SECURITY ALERT: The encryption key is set to the default value. " +
            "DO NOT USE DEFAULT KEY IN PRODUCTION!" +
            " Please change the ecos.secret.encryption.key immediately! *****"
    }

    private val isProdWithDefaultKey = fun(): Boolean {
        return environment.activeProfiles.contains(PROD_PROFILE) &&
            !environment.activeProfiles.contains(TEST_PROFILE) &&
            currentBase64Key == DEFAULT_ENCRYPTION_KEY
    }

    @PostConstruct
    fun init() {
        if (isProdWithDefaultKey()) {
            log.error { SECURITY_MSG }
        }
    }

    override fun getCurrentSecretKey(): SecretKey {
        if (isProdWithDefaultKey()) {
            log.warn { SECURITY_MSG }
        }

        val decodedKey = Base64.getDecoder().decode(currentBase64Key)
        return SecretKeySpec(decodedKey, ENCRYPTION_KEY_ALGORITHM)
    }

    override fun getCurrentSecretKeyBase64(): String {
        return currentBase64Key
    }

    override fun getCurrentAlgorithm(): String {
        return currentAlgorithm
    }

    override fun getCurrentIvSize(): Int {
        return currentIvSize
    }

    override fun getCurrentTagSize(): Int {
        return currentTagSize
    }

    override fun getPreviousSecretKey(): SecretKey? {
        if (previousBase64Key.isEmpty()) {
            return null
        }

        val decodedKey = Base64.getDecoder().decode(previousBase64Key)
        return SecretKeySpec(decodedKey, ENCRYPTION_KEY_ALGORITHM)
    }

    override fun getPreviousSecretKeyBase64(): String? {
        return previousBase64Key
    }
}
