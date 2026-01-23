package com.worldcoin.idkit_kotlin.impl.session

import com.worldcoin.idkit_kotlin.*
import com.worldcoin.idkit_kotlin.impl.Crypto
import com.worldcoin.idkit_kotlin.impl.CreateRequestPayload
import com.worldcoin.idkit_kotlin.impl.CreateCredentialCategoryRequestPayload
import com.worldcoin.idkit_kotlin.impl.EncryptablePayload
import com.worldcoin.idkit_kotlin.impl.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import org.kotlincrypto.hash.sha3.Keccak256
import java.io.IOException
import java.math.BigInteger

/**
 * Factory for creating Session instances.
 */
internal object SessionFactory {
    
    suspend fun create(
        appID: AppID,
        action: String,
        verificationLevel: VerificationLevel = VerificationLevel.ORB,
        bridgeURL: BridgeURL = BridgeURL.default,
        signal: String = "",
        actionDescription: String? = null
    ): Session {
        val payload = CreateRequestPayload(
            appID = appID,
            action = action,
            signal = encodeSignal(signal),
            actionDescription = actionDescription,
            verificationLevel = verificationLevel
        )

        return createSessionInternal(payload, bridgeURL, ConnectUrlType.WLD)
    }

    suspend fun createCredentialCategorySession(
        appID: AppID,
        action: String,
        credentialCategory: Set<CredentialCategory>,
        bridgeURL: BridgeURL = BridgeURL.default,
        signal: String = "",
        actionDescription: String? = null
    ): Session {
        if (credentialCategory.isEmpty()) {
            throw AppErrorThrowable(
                AppError.InvalidInput("credentialCategory must not be empty. Please provide at least one CredentialCategory.")
            )
        }
        
        val payload = CreateCredentialCategoryRequestPayload(
            appID = appID,
            action = action,
            signal = encodeSignal(signal),
            actionDescription = actionDescription,
            credentialCategory = credentialCategory,
        )

        return createSessionInternal(payload, bridgeURL, ConnectUrlType.CREDENTIAL_CATEGORY)
    }

    private suspend fun createSessionInternal(
        payload: EncryptablePayload,
        bridgeURL: BridgeURL,
        connectUrlType: ConnectUrlType,
    ): Session = withContext(Dispatchers.IO) {
        try {
            val encryptionResult = Crypto.encryptPayload(payload)
            val response = NetworkClient.createRequest(encryptionResult.payload, bridgeURL)
            DefaultSession(response.requestId, encryptionResult.key, bridgeURL, connectUrlType)
        } catch (e: IOException) {
            throw AppErrorThrowable(
                AppError.GenericError("Failed to connect to the Bridge server: ${e.message}")
            )
        } catch (e: SerializationException) {
            throw AppErrorThrowable(
                AppError.GenericError("JSON parsing failed: ${e.message}")
            )
        }
    }
    
    private fun encodeSignal(signal: String): String {
        val bytes = signal.toByteArray()
        val keccak256 = bytes.keccak256()
        return "0x" + BigInteger(1, keccak256).shiftRight(8).toString(16)
    }

    private fun ByteArray.keccak256(): ByteArray {
        return Keccak256().digest(this)
    }
}

/**
 * Types of connect URLs that can be generated for sessions.
 */
internal enum class ConnectUrlType(val type: String) {
    /** Standard World ID verification URL */
    WLD("wld"),

    /** Credential category verification URL */
    CREDENTIAL_CATEGORY("cred")
}
