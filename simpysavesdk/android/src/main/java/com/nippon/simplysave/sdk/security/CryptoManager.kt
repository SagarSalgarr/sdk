package com.nippon.simplysave.sdk.security

import android.content.Context
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM. IV (12 bytes) is prepended to ciphertext.
 * Uses KeystoreManager key when available; otherwise uses in-memory key for development.
 */
object CryptoManager {

    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun encrypt(plainBytes: ByteArray, context: Context): ByteArray {
        val key = try {
            KeystoreManager.getOrCreateKey(context)
        } catch (_: Exception) {
            SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
        }
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        }
        val ciphertext = cipher.doFinal(plainBytes)
        return iv + ciphertext
    }

    fun decrypt(encryptedBytes: ByteArray, context: Context): ByteArray {
        if (encryptedBytes.size <= GCM_IV_LENGTH) return encryptedBytes
        return try {
            val key = KeystoreManager.getOrCreateKey(context)
            val iv = encryptedBytes.copyOfRange(0, GCM_IV_LENGTH)
            val cipherBytes = encryptedBytes.copyOfRange(GCM_IV_LENGTH, encryptedBytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            }
            cipher.doFinal(cipherBytes)
        } catch (_: Exception) {
            encryptedBytes
        }
    }

    fun decrypt(encryptedBytes: ByteArray): ByteArray {
        if (encryptedBytes.size <= GCM_IV_LENGTH) return encryptedBytes
        return encryptedBytes
    }
}
