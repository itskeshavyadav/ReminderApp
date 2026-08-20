package com.example.data.crypto

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object EncryptionUtil {
    private const val GCM_TAG_LENGTH_BIT = 128
    private const val GCM_IV_LENGTH_BYTE = 12
    private const val SALT_LENGTH_BYTE = 16
    private const val PBKDF2_ITERATIONS = 10000
    private const val KEY_LENGTH_BIT = 256

    data class EncryptionResult(
        val saltBase64: String,
        val ivBase64: String,
        val cipherTextBase64: String
    )

    fun encrypt(plainText: String, passphrase: CharArray): EncryptionResult {
        val random = SecureRandom()
        
        // Generate random salt
        val salt = ByteArray(SALT_LENGTH_BYTE)
        random.nextBytes(salt)

        // Generate random IV for GCM
        val iv = ByteArray(GCM_IV_LENGTH_BYTE)
        random.nextBytes(iv)

        // Derive 256-bit AES key using PBKDF2
        val keySpec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BIT)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = keyFactory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(keyBytes, "AES")

        // Encrypt with AES/GCM/NoPadding
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        return EncryptionResult(
            saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            cipherTextBase64 = Base64.encodeToString(cipherText, Base64.NO_WRAP)
        )
    }

    fun decrypt(
        cipherTextBase64: String,
        saltBase64: String,
        ivBase64: String,
        passphrase: CharArray
    ): String {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val cipherBytes = Base64.decode(cipherTextBase64, Base64.NO_WRAP)

        // Derive key with same parameters
        val keySpec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BIT)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = keyFactory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        val decryptedBytes = cipher.doFinal(cipherBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }
}
