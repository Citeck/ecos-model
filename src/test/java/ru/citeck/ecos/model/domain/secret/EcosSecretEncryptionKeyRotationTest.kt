package ru.citeck.ecos.model.domain.secret

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.model.domain.secret.dto.EncryptionMeta
import ru.citeck.ecos.model.domain.secret.repo.EcosSecretEntity
import ru.citeck.ecos.model.domain.secret.repo.EcosSecretRepo
import ru.citeck.ecos.model.domain.secret.service.*
import ru.citeck.ecos.model.domain.secret.service.EncryptionConfigFromProperties.Companion.ENCRYPTION_KEY_ALGORITHM
import ru.citeck.ecos.secrets.lib.secret.EcosSecretType
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import java.util.*
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosModelApp::class])
class EcosSecretEncryptionKeyRotationTest {

    @Autowired
    private lateinit var ecosSecretService: EcosSecretService

    @Autowired
    private lateinit var ecosSecretRepo: EcosSecretRepo

    @Autowired
    private lateinit var rotator: EcosSecretEncryptionKeyRotator

    @Autowired
    private lateinit var secretKeyEncoder: SecretKeyEncoder

    @Autowired
    private lateinit var secretEncryptionConfig: EcosSecretEncryptionConfigProvider

    @Autowired
    private lateinit var ecosSecretEncryption: EcosSecretEncryption

    @SpyBean
    private lateinit var ecosSecretEncryptionConfigProvider: EcosSecretEncryptionConfigProvider

    companion object {
        private val rawData = """
            {
              "username": "admin",
              "password": "admin_password"
            }
        """.trimIndent().toByteArray()
    }

    @Test
    fun `not encrypted data should be encrypted with current key`() {
        val id = "not-encrypted-secret"

        val notEncryptedSecretEntity = EcosSecretEntity.create(id)
            .apply {
                type = EcosSecretType.BASIC.name
                data = rawData
            }

        val savedSecret = ecosSecretRepo.save(notEncryptedSecretEntity)

        assertThat(savedSecret.encryptionKeyHash).isNull()
        assertThat(savedSecret.encryptionMeta).isNull()

        rotator.rotateKey()

        val rotatedSecret = ecosSecretRepo.findByExtId(id)!!
        val currentKeyBase64 = secretEncryptionConfig.getCurrentSecretKeyBase64()

        assertThat(secretKeyEncoder.matchesKey(currentKeyBase64, rotatedSecret.encryptionKeyHash!!)).isTrue

        val encryptionMeta = Json.mapper.read(rotatedSecret.encryptionMeta, EncryptionMeta::class.java)!!

        assertThat(encryptionMeta.algorithm).isEqualTo(secretEncryptionConfig.getCurrentAlgorithm())
        assertThat(encryptionMeta.ivSize).isEqualTo(secretEncryptionConfig.getCurrentIvSize())
        assertThat(encryptionMeta.tagSize).isEqualTo(secretEncryptionConfig.getCurrentTagSize())

        val decryptedData = ecosSecretEncryption.decrypt(
            EncryptData(
                data = rotatedSecret.data!!,
                key = secretEncryptionConfig.getCurrentSecretKey(),
                algorithm = encryptionMeta.algorithm,
                ivSize = encryptionMeta.ivSize,
                tagSize = encryptionMeta.tagSize
            )
        )

        assertThat(decryptedData).isEqualTo(rawData)
    }

    @Test
    fun `test re encryption data on key rotation`() {

        val secretId = "test-re-encryption-secret-1"
        val secret = createTestSecret(secretId)

        val moreSecretId = "test-re-encryption-secret-2"
        val moreSecret = createTestSecret(moreSecretId)

        ecosSecretService.save(secret)
        ecosSecretService.save(moreSecret)

        val newKey = "3vD09/12FciEgNtO6hYKLw=="

        Mockito.`when`(ecosSecretEncryptionConfigProvider.getCurrentSecretKeyBase64()).thenReturn(newKey)
        Mockito.`when`(ecosSecretEncryptionConfigProvider.getCurrentSecretKey()).thenReturn(newKey.toSecretKey())

        Mockito.`when`(ecosSecretEncryptionConfigProvider.getPreviousSecretKeyBase64()).thenReturn(
            EncryptionConfigFromProperties.DEFAULT_ENCRYPTION_KEY
        )
        Mockito.`when`(ecosSecretEncryptionConfigProvider.getPreviousSecretKey()).thenReturn(
            EncryptionConfigFromProperties.DEFAULT_ENCRYPTION_KEY.toSecretKey()
        )

        rotator.rotateKey()

        val secretEntity = ecosSecretRepo.findByExtId(secretId)!!
        val moreSecretEntity = ecosSecretRepo.findByExtId(moreSecretId)!!

        assertThat(secretKeyEncoder.matchesKey(newKey, secretEntity.encryptionKeyHash!!)).isTrue
        assertThat(secretKeyEncoder.matchesKey(newKey, moreSecretEntity.encryptionKeyHash!!)).isTrue

        val decryptedSecretData = ecosSecretEncryption.decrypt(
            EncryptData(
                data = secretEntity.data!!,
                key = newKey.toSecretKey(),
                algorithm = secretEncryptionConfig.getCurrentAlgorithm(),
                ivSize = secretEncryptionConfig.getCurrentIvSize(),
                tagSize = secretEncryptionConfig.getCurrentTagSize()
            )
        )

        val decryptedMoreSecretData = ecosSecretEncryption.decrypt(
            EncryptData(
                data = moreSecretEntity.data!!,
                key = newKey.toSecretKey(),
                algorithm = secretEncryptionConfig.getCurrentAlgorithm(),
                ivSize = secretEncryptionConfig.getCurrentIvSize(),
                tagSize = secretEncryptionConfig.getCurrentTagSize()
            )
        )

        val rawData = ObjectData.create(rawData)

        assertThat(ObjectData.create(decryptedSecretData)).isEqualTo(rawData)
        assertThat(ObjectData.create(decryptedMoreSecretData)).isEqualTo(rawData)
    }

    @Test
    fun `encrypted data should remain consistent after multiple rotations`() {
        val secretId = "consistent-encryption-secret"
        val secret = createTestSecret(secretId)

        ecosSecretService.save(secret)

        val initialKey = secretEncryptionConfig.getCurrentSecretKeyBase64()
        val newKey = "3vD09/12FciEgNtO6hYKLw=="

        // First rotation
        Mockito.`when`(ecosSecretEncryptionConfigProvider.getCurrentSecretKeyBase64()).thenReturn(newKey)
        Mockito.`when`(ecosSecretEncryptionConfigProvider.getCurrentSecretKey()).thenReturn(newKey.toSecretKey())
        Mockito.`when`(ecosSecretEncryptionConfigProvider.getPreviousSecretKeyBase64()).thenReturn(initialKey)
        Mockito.`when`(ecosSecretEncryptionConfigProvider.getPreviousSecretKey()).thenReturn(initialKey.toSecretKey())

        rotator.rotateKey()

        // Second rotation back to initial key
        Mockito.`when`(ecosSecretEncryptionConfigProvider.getCurrentSecretKeyBase64()).thenReturn(initialKey)
        Mockito.`when`(ecosSecretEncryptionConfigProvider.getCurrentSecretKey()).thenReturn(initialKey.toSecretKey())
        Mockito.`when`(ecosSecretEncryptionConfigProvider.getPreviousSecretKeyBase64()).thenReturn(newKey)
        Mockito.`when`(ecosSecretEncryptionConfigProvider.getPreviousSecretKey()).thenReturn(newKey.toSecretKey())

        rotator.rotateKey()

        val secretEntity = ecosSecretRepo.findByExtId(secretId)!!

        assertThat(secretKeyEncoder.matchesKey(initialKey, secretEntity.encryptionKeyHash!!)).isTrue

        val decryptedData = ecosSecretEncryption.decrypt(
            EncryptData(
                data = secretEntity.data!!,
                key = initialKey.toSecretKey(),
                algorithm = secretEncryptionConfig.getCurrentAlgorithm(),
                ivSize = secretEncryptionConfig.getCurrentIvSize(),
                tagSize = secretEncryptionConfig.getCurrentTagSize()
            )
        )

        assertThat(ObjectData.create(decryptedData)).isEqualTo(ObjectData.create(rawData))
    }

    private fun createTestSecret(id: String): EcosSecretDto {
        return EcosSecretDto(
            id = id,
            name = MLText.EMPTY,
            type = EcosSecretType.BASIC.name,
            data = ObjectData.create(rawData)
        )
    }

    private fun String.toSecretKey(): SecretKey {
        val decodedKey = Base64.getDecoder().decode(this)
        return SecretKeySpec(decodedKey, ENCRYPTION_KEY_ALGORITHM)
    }
}
