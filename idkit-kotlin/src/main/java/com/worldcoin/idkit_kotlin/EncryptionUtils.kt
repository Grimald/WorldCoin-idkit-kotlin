package com.worldcoin.idkit_kotlin

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Throws(Exception::class)
internal fun EncryptablePayload.encryptPayload(
    key: SecretKey,
    nonce: ByteArray
): Payload {
    val jsonString = Json.encodeToString(this)
    val data = jsonString.toByteArray()

    // Initialize the cipher with AES/GCM/NoPadding
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    val spec = GCMParameterSpec(128, nonce)  // 128-bit authentication tag
    cipher.init(Cipher.ENCRYPT_MODE, key, spec)

    // Encrypt the data
    val sealedBox = cipher.doFinal(data)

    // Extract the ciphertext and the tag
    val ciphertext = sealedBox.copyOfRange(0, sealedBox.size - 16)
    val tag = sealedBox.copyOfRange(sealedBox.size - 16, sealedBox.size)

    // Combine ciphertext and tag
    val payload = ciphertext + tag

    // Return the Payload object containing the IV and the encrypted data
    return Payload(
        iv = Base64.getEncoder().encodeToString(nonce),
        payload = Base64.getEncoder().encodeToString(payload)
    )
}
