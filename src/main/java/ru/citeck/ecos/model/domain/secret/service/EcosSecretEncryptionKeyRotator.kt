package ru.citeck.ecos.model.domain.secret.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.model.domain.secret.dto.EncryptionMeta
import ru.citeck.ecos.model.domain.secret.repo.EcosSecretEntity
import ru.citeck.ecos.model.domain.secret.repo.EcosSecretRepo
import javax.annotation.PostConstruct

@Component
@Transactional
class EcosSecretEncryptionKeyRotator(
    private val ecosSecretRepo: EcosSecretRepo,
    private val encryptionConfig: EcosSecretEncryptionConfigProvider,
    private val secretKeyEncoder: SecretKeyEncoder,
    private val ecosSecretEncryption: EcosSecretEncryption
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    @PostConstruct
    fun rotateOnStartup() {
        rotateKey()
    }

    fun rotateKey() {

        val currentKeyBase64 = encryptionConfig.getCurrentSecretKeyBase64()

        var page = 0
        val batchSize = 10

        while (true) {
            val secrets = ecosSecretRepo.findAll(PageRequest.of(page, batchSize)).content
            if (secrets.isEmpty()) {
                break
            }

            val updatedSecrets = mutableListOf<EcosSecretEntity>()

            for (secret in secrets) {
                // if secret is not encrypted, encrypt it with current key
                if (secret.encryptionKeyHash.isNullOrBlank() && secret.data?.isNotEmpty() == true) {
                    val encryptedSecret = encryptSecretWithRawData(secret)
                    updatedSecrets.add(encryptedSecret)

                    continue
                }

                // if secret is encrypted with not current key, rotate it
                if (secret.encryptionKeyHash?.isNotEmpty() == true &&
                    secretKeyEncoder.notMatchesKey(currentKeyBase64, secret.encryptionKeyHash!!)
                ) {
                    rotateEncryptedDataWithNewKey(secret)?.let {
                        updatedSecrets.add(it)
                    }

                    continue
                }
            }

            ecosSecretRepo.saveAll(updatedSecrets)
            page++
        }
    }

    private fun encryptSecretWithRawData(secret: EcosSecretEntity): EcosSecretEntity {
        val data = secret.data ?: error("Secret data is empty")

        log.debug { "Encrypting secret ${secret.extId} with current key" }

        return encryptSecretWithData(secret, data)
    }

    private fun encryptSecretWithData(secret: EcosSecretEntity, data: ByteArray): EcosSecretEntity {
        val encryptedData = ecosSecretEncryption.encrypt(
            EncryptData(
                data = data,
                key = encryptionConfig.getCurrentSecretKey(),
                algorithm = encryptionConfig.getCurrentAlgorithm(),
                ivSize = encryptionConfig.getCurrentIvSize(),
                tagSize = encryptionConfig.getCurrentTagSize()
            )
        )
        val keyHash = secretKeyEncoder.hashKey(encryptionConfig.getCurrentSecretKeyBase64())
        val encryptionMeta = EncryptionMeta(
            algorithm = encryptionConfig.getCurrentAlgorithm(),
            ivSize = encryptionConfig.getCurrentIvSize(),
            tagSize = encryptionConfig.getCurrentTagSize()
        )

        secret.data = encryptedData
        secret.encryptionKeyHash = keyHash
        secret.encryptionMeta = Json.mapper.toBytesNotNull(encryptionMeta)

        return secret
    }

    private fun rotateEncryptedDataWithNewKey(secret: EcosSecretEntity): EcosSecretEntity? {
        val previousKey = encryptionConfig.getPreviousSecretKey() ?: let {
            log.error {
                "Cannot rotate secret ${secret.extId}. " +
                    "Secret is encrypted with not actual key, but previous key is not found"
            }
            return null
        }
        val previousKeyBase64 = encryptionConfig.getPreviousSecretKeyBase64() ?: let {
            log.error {
                "Cannot rotate secret ${secret.extId}. " +
                    "Secret is encrypted with not actual key, but previous key is not found"
            }
            return null
        }

        if (secretKeyEncoder.notMatchesKey(previousKeyBase64, secret.encryptionKeyHash!!)) {
            log.error { "Cannot rotate secret ${secret.extId}. Secret is encrypted with not actual/previous key" }
            return null
        }

        val encryptionMeta = Json.mapper.read(secret.encryptionMeta, EncryptionMeta::class.java) ?: let {
            log.error { "Cannot rotate secret ${secret.extId}. Cannot read encryption meta" }
            return null
        }

        val decryptedData = ecosSecretEncryption.decrypt(
            EncryptData(
                data = secret.data!!,
                key = previousKey,
                algorithm = encryptionMeta.algorithm,
                ivSize = encryptionMeta.ivSize,
                tagSize = encryptionMeta.tagSize
            )
        )

        log.debug { "Rotating secret ${secret.extId} with new key" }

        return encryptSecretWithData(secret, decryptedData)
    }
}
