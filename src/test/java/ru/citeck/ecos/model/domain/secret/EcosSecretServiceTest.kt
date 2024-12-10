package ru.citeck.ecos.model.domain.secret

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import ru.citeck.ecos.commons.data.MLText
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.Json
import ru.citeck.ecos.model.EcosModelApp
import ru.citeck.ecos.model.domain.secret.dto.EncryptionMeta
import ru.citeck.ecos.model.domain.secret.repo.EcosSecretRepo
import ru.citeck.ecos.model.domain.secret.service.EcosSecretDto
import ru.citeck.ecos.model.domain.secret.service.EcosSecretEncryptionConfigProvider
import ru.citeck.ecos.model.domain.secret.service.EcosSecretService
import ru.citeck.ecos.model.domain.secret.service.SecretKeyEncoder
import ru.citeck.ecos.secrets.lib.secret.EcosSecretType
import ru.citeck.ecos.webapp.lib.spring.test.extension.EcosSpringExtension
import kotlin.test.assertTrue

@ExtendWith(EcosSpringExtension::class)
@SpringBootTest(classes = [EcosModelApp::class])
class EcosSecretServiceTest {

    @Autowired
    private lateinit var ecosSecretService: EcosSecretService

    @Autowired
    private lateinit var ecosSecretRepo: EcosSecretRepo

    @Autowired
    private lateinit var secretKeyEncoder: SecretKeyEncoder

    @Autowired
    private lateinit var secretEncryptionInfoProvider: EcosSecretEncryptionConfigProvider

    @Test
    fun `test save and read secret`() {
        val id = "save-read-test-secret"
        val secret = createTestSecret(id)

        ecosSecretService.save(secret)

        val basicData = ecosSecretService.getSecret(id)!!.getBasicData()

        assertThat(basicData.username).isEqualTo("admin")
        assertThat(basicData.password).isEqualTo("admin_password")
    }

    @Test
    fun `save secret should save key hash`() {
        val id = "save-encryption-key-hash-test-secret"
        val secret = createTestSecret(id)

        ecosSecretService.save(secret)

        val savedSecret = ecosSecretRepo.findByExtId(id)
        assertThat(savedSecret).isNotNull

        val currentKey = secretEncryptionInfoProvider.getCurrentSecretKeyBase64()

        assertTrue(secretKeyEncoder.matchesKey(currentKey, savedSecret!!.encryptionKeyHash!!))
    }

    @Test
    fun `save secret should save encryption meta`() {
        val id = "save-encryption-meta-test-secret"
        val secret = createTestSecret(id)

        ecosSecretService.save(secret)

        val savedSecret = ecosSecretRepo.findByExtId(id)
        assertThat(savedSecret).isNotNull

        val encryptionMeta = Json.mapper.read(savedSecret!!.encryptionMeta, EncryptionMeta::class.java)!!

        assertThat(encryptionMeta.algorithm).isEqualTo(secretEncryptionInfoProvider.getCurrentAlgorithm())
        assertThat(encryptionMeta.ivSize).isEqualTo(secretEncryptionInfoProvider.getCurrentIvSize())
        assertThat(encryptionMeta.tagSize).isEqualTo(secretEncryptionInfoProvider.getCurrentTagSize())
    }

    private fun createTestSecret(id: String): EcosSecretDto {
        return EcosSecretDto(
            id = id,
            name = MLText.EMPTY,
            type = EcosSecretType.BASIC.name,
            data = ObjectData.create(
                """
                {
                  "username": "admin",
                  "password": "admin_password"
                }
                """.trimIndent()
            )
        )
    }
}
