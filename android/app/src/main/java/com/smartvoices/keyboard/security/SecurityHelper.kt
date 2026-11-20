package com.smartvoices.keyboard.security

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object SecurityHelper {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12
    private const val SALT_LENGTH_BYTE = 16
    private const val PBKDF2_ITERATIONS = 100000
    private const val KEY_LENGTH_BIT = 256

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BIT)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Encrypt text with AES-256-GCM using standard PBKDF2 salt and random IV
     */
    fun encrypt(text: String, passphrase: String): String {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH_BYTE).apply { random.nextBytes(this) }
        val iv = ByteArray(IV_LENGTH_BYTE).apply { random.nextBytes(this) }

        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BIT, iv))

        val cipherBytes = cipher.doFinal(text.toByteArray(Charsets.UTF_8))

        // In GCM, authTag is automatically appended at the end of cipherBytes (last 16 bytes)
        val authTag = cipherBytes.takeLast(16).toByteArray()
        val encryptedData = cipherBytes.dropLast(16).toByteArray()

        val json = JSONObject().apply {
            put("alg", "aes-256-gcm")
            put("salt", bytesToHex(salt))
            put("iv", bytesToHex(iv))
            put("tag", bytesToHex(authTag))
            put("data", bytesToHex(encryptedData))
        }

        return Base64.encodeToString(json.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    /**
     * Decrypt text with AES-256-GCM
     */
    fun decrypt(encryptedBase64: String, passphrase: String): String {
        val jsonStr = String(Base64.decode(encryptedBase64, Base64.NO_WRAP), Charsets.UTF_8)
        val json = JSONObject(jsonStr)

        val salt = hexToBytes(json.getString("salt"))
        val iv = hexToBytes(json.getString("iv"))
        val tag = hexToBytes(json.getString("tag"))
        val data = hexToBytes(json.getString("data"))

        // Recombine cipher data with authTag
        val cipherBytes = data + tag

        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BIT, iv))

        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, Charsets.UTF_8)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789abcdef"
        val result = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val i = b.toInt() and 0xFF
            result.append(hexChars[i shr 4])
            result.append(hexChars[i and 0x0F])
        }
        return result.toString()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
