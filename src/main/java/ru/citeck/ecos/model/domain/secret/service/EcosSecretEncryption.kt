package ru.citeck.ecos.model.domain.secret.service

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Component
class EcosSecretEncryption {

    fun encrypt(encryptData: EncryptData): ByteArray {
        with(encryptData) {
            val cipher = Cipher.getInstance(algorithm)
            val iv = ByteArray(ivSize)

            // Generate a random IV
            SecureRandom().nextBytes(iv)

            val gcmSpec = GCMParameterSpec(tagSize, iv)

            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)
            val cipherText = cipher.doFinal(data)

            // Combine IV and ciphertext for storage
            val ivAndCipherText = iv + cipherText

            return ivAndCipherText
        }
    }

    fun decrypt(decryptData: EncryptData): ByteArray {
        with(decryptData) {

            // Extract IV and ciphertext
            val iv = data.copyOfRange(0, ivSize)
            val cipherText = data.copyOfRange(ivSize, data.size)
            val gcmSpec = GCMParameterSpec(tagSize, iv)

            val cipher = Cipher.getInstance(algorithm)
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

            return cipher.doFinal(cipherText)
        }
    }
}

data class EncryptData(
    val data: ByteArray,

    val key: SecretKey,
    val algorithm: String,
    val ivSize: Int,
    val tagSize: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptData

        if (ivSize != other.ivSize) return false
        if (tagSize != other.tagSize) return false
        if (!data.contentEquals(other.data)) return false
        if (key != other.key) return false
        if (algorithm != other.algorithm) return false

        return true
    }

    override fun hashCode(): Int {
        var result = ivSize
        result = 31 * result + tagSize
        result = 31 * result + data.contentHashCode()
        result = 31 * result + key.hashCode()
        result = 31 * result + algorithm.hashCode()
        return result
    }
}

@Component
class SecretKeyEncoder(
    private val encoder: PasswordEncoder
) {

    fun hashKey(key: String): String {
        return encoder.encode(key)
    }

    fun matchesKey(key: String, hash: String): Boolean {
        return encoder.matches(key, hash)
    }

    fun notMatchesKey(key: String, hash: String): Boolean {
        return !encoder.matches(key, hash)
    }
}
