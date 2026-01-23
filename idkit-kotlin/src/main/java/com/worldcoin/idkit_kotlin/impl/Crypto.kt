package com.worldcoin.idkit_kotlin.impl

import com.worldcoin.idkit_kotlin.impl.network.BridgeResponse
import com.worldcoin.idkit_kotlin.impl.network.EncryptedPayload
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val AES_KEY_BYTES = 32
private const val AES_GCM_IV_BYTES = 12
private const val AES_GCM_TAG_BITS = 128
private const val AES_GCM_TAG_BYTES = 16

/**
 * Cryptographic utilities for encryption and decryption operations.
 */
internal object Crypto {

    private val secureRandom = SecureRandom()
    
    /**
     * Encrypts an encryptable payload using AES-GCM encryption.
     * 
     * @param payload The payload to encrypt
     * @param key The encryption key (optional, will generate if not provided)
     * @param iv The initialization vector (optional, will generate if not provided)
     * @return An EncryptionResult containing the encrypted data and key
     */
    internal fun encryptPayload(
        payload: EncryptablePayload,
        key: SecretKey? = null,
        iv: ByteArray? = null
    ): EncryptionResult {
        val encryptionKey = key ?: generateAESKey()
        val encryptionIV = iv ?: generateIV()
        
        val jsonString = Json.encodeToString(payload)
        val data = jsonString.toByteArray()
        
        val sealedBox = encryptAESGCM(data, encryptionKey, encryptionIV)
        
        val ciphertext = sealedBox.copyOfRange(0, sealedBox.size - AES_GCM_TAG_BYTES)
        val tag = sealedBox.copyOfRange(sealedBox.size - AES_GCM_TAG_BYTES, sealedBox.size)
        
        // Combine ciphertext and tag
        val payloadData = ciphertext + tag
        
        // Return the EncryptedPayload object containing the IV and the encrypted data
        val encrypted = EncryptedPayload(
            iv = Base64.getEncoder().encodeToString(encryptionIV),
            payload = Base64.getEncoder().encodeToString(payloadData)
        )
        
        return EncryptionResult(encrypted, encryptionKey)
    }

    internal fun generateAESKey(): SecretKey {
        val keyBytes = ByteArray(AES_KEY_BYTES)
        secureRandom.nextBytes(keyBytes)

        return SecretKeySpec(keyBytes, "AES")
    }

    internal fun generateIV(): ByteArray {
        val iv = ByteArray(AES_GCM_IV_BYTES)
        secureRandom.nextBytes(iv)

        return iv
    }

    internal fun encryptAESGCM(data: ByteArray, key: SecretKey, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(AES_GCM_TAG_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        return cipher.doFinal(data)
    }
    
    /**
     * Decrypts [EncryptedPayload] using AES-GCM decryption.
     * 
     * @param payload The encrypted payload containing encrypted data and IV
     * @param key The decryption key
     * @return The decrypted BridgeResponse
     */
    internal fun decryptPayload(payload: EncryptedPayload, key: SecretKey): BridgeResponse {
        // Decode the Base64 encoded payload and IV (nonce)
        val decodedPayload = Base64.getDecoder().decode(payload.payload)
        val decodedIV = Base64.getDecoder().decode(payload.iv)
        
        val decryptedData = decryptAESGCM(decodedPayload, key, decodedIV)
        
        // Decode the decrypted data back to an object
        return Json.decodeFromString<BridgeResponse>(String(decryptedData))
    }

    private fun decryptAESGCM(encryptedData: ByteArray, key: SecretKey, iv: ByteArray): ByteArray {
        val ciphertext = encryptedData.copyOfRange(0, encryptedData.size - AES_GCM_TAG_BYTES)
        val authTag = encryptedData.copyOfRange(encryptedData.size - AES_GCM_TAG_BYTES, encryptedData.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(AES_GCM_TAG_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        return cipher.doFinal(ciphertext + authTag)
    }
}

/**
 * Result of an encryption operation.
 * 
 * @param payload The encrypted payload containing IV and encrypted data
 * @param key The encryption key that was used
 */
internal data class EncryptionResult(
    val payload: EncryptedPayload,
    val key: SecretKey
)
