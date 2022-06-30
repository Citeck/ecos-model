package ru.citeck.ecos.model

import com.google.common.primitives.Longs
import org.apache.commons.codec.binary.Base32
import org.junit.jupiter.api.Test
import ru.citeck.ecos.model.type.service.utils.EcosModelTypeUtils
import java.util.*
import java.util.zip.CRC32

class AbcTest {

    @Test
    fun test() {

        val digests = mutableSetOf<String>()
        val alphabet = "abcd"
/*
        for (i in 900000000..1000000000) {

            var string = ""
            var num = i
            if (num >= alphabet.length) {
                while (num >= alphabet.length) {
                    string += alphabet[num % alphabet.length]
                    num /= alphabet.length
                }
                string += alphabet[num - 1]
            } else {
                string += alphabet[num]
            }
            val crc = CRC32()
            crc.update(string.toByteArray())

            //val digest = DigestUtils.getDigest(string.toByteArray(), DigestAlgorithm.SHA256)
            val hash = crc.value.toString(16).padStart(16).substring(8)
            if (!digests.add(hash)) {
                println("$string COLLISION: " + hash)
            }
            if (i % 10000 == 0) {
                println("RUBEZH: " + i + " $string")
            }
        }*/

        val base32 = Base32()
        println(encode("contract"))
        println(EcosModelTypeUtils.generateEmodelSourceId("contract"))
        println(encode("agreement"))
        println(EcosModelTypeUtils.generateEmodelSourceId("agreement"))
        println(encode("currency"))
        println(EcosModelTypeUtils.generateEmodelSourceId("currency"))
        println(encode("counterparty"))
        println(EcosModelTypeUtils.generateEmodelSourceId("counterparty"))
        println(encode("schet_na_oplatu"))
        println(EcosModelTypeUtils.generateEmodelSourceId("schet_na_oplatu"))
        println(encode("counterparty-with-long-value/and-some-kind-with-it"))
        println(EcosModelTypeUtils.generateEmodelSourceId("counterparty-with-long-value/and-some-kind-with-it"))

        // val crc = CRC32()
        // crc.update("contracts".toByteArray())

        // println(crc.value.toString(16).padStart(8, '0'))
        // println(1L.toString(16).padStart(8, '0'))

        // println(Base64.getEncoder().encodeToString(longToUInt32ByteArray(crc.value)))
        // println(Base64.getEncoder().encodeToString(longToUInt32ByteArray(Long.MAX_VALUE)))
        // println(Long.MAX_VALUE.toString(16))
        // println(Long.MAX_VALUE.toString(32))
        // println(Long.MAX_VALUE.toString(Character.MAX_RADIX))
        // "t-ed82cd11"

        // encode(Long.MAX_VALUE)
        // encode(Long.MIN_VALUE)
    }

    fun encode(value: String): String {
        val crc = CRC32()
        crc.update(value.toByteArray())
        val base32 = Base32()
        return base32.encodeToString(Longs.toByteArray(crc.value)).lowercase()
        // println(Base64.getEncoder().encodeToString(longToUInt32ByteArray(long)).replace("/", "_").replace("+", "."))
    }

    fun longToUInt32ByteArray(value: Long): ByteArray {
        val bytes = ByteArray(4)
        bytes[3] = (value and 0xFFFF).toByte()
        bytes[2] = ((value ushr 8) and 0xFFFF).toByte()
        bytes[1] = ((value ushr 16) and 0xFFFF).toByte()
        bytes[0] = ((value ushr 24) and 0xFFFF).toByte()
        return bytes
    }
}
