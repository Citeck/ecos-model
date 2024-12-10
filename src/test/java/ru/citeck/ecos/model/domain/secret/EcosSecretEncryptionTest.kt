package ru.citeck.ecos.model.domain.secret

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.model.domain.secret.service.EcosSecretEncryption
import ru.citeck.ecos.model.domain.secret.service.EcosSecretEncryptionConfigProvider
import ru.citeck.ecos.model.domain.secret.service.EncryptData
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosModelApp::class])
class EcosSecretEncryptionTest {

    @Autowired
    private lateinit var ecosSecretEncryption: EcosSecretEncryption

    @Autowired
    private lateinit var encryptionInfo: EcosSecretEncryptionConfigProvider

    @Test
    fun `test encryption and decryption bytes`() {
        val textToEncrypt = "After all this time? – Always".toByteArray()
        val dataToEncrypt = generateEncryptData(textToEncrypt)

        val encryptedBytes = ecosSecretEncryption.encrypt(dataToEncrypt)
        val decryptedBytes = ecosSecretEncryption.decrypt(generateEncryptData(encryptedBytes))

        assertThat(encryptedBytes).isNotEqualTo(textToEncrypt)
        assertThat(decryptedBytes).isEqualTo(textToEncrypt)
    }

    @Test
    fun `test single byte encryption and decryption`() {
        val bytesToEncrypt = byteArrayOf(0x01)
        val dataToEncrypt = generateEncryptData(bytesToEncrypt)

        val encryptedBytes = ecosSecretEncryption.encrypt(dataToEncrypt)
        val decryptedBytes = ecosSecretEncryption.decrypt(generateEncryptData(encryptedBytes))

        assertThat(encryptedBytes).isNotEqualTo(bytesToEncrypt)
        assertThat(decryptedBytes).isEqualTo(bytesToEncrypt)
    }

    @Test
    fun `test large byte array encryption and decryption`() {
        val bytesToEncrypt = ByteArray(100000) { it.toByte() }
        val dataToEncrypt = generateEncryptData(bytesToEncrypt)

        val encryptedBytes = ecosSecretEncryption.encrypt(dataToEncrypt)
        val decryptedBytes = ecosSecretEncryption.decrypt(generateEncryptData(encryptedBytes))

        assertThat(encryptedBytes).isNotEqualTo(bytesToEncrypt)
        assertThat(decryptedBytes).isEqualTo(bytesToEncrypt)
    }

    private fun generateEncryptData(data: ByteArray): EncryptData {
        return EncryptData(
            data = data,
            key = encryptionInfo.getCurrentSecretKey(),
            algorithm = encryptionInfo.getCurrentAlgorithm(),
            ivSize = encryptionInfo.getCurrentIvSize(),
            tagSize = encryptionInfo.getCurrentTagSize()
        )
    }
}
