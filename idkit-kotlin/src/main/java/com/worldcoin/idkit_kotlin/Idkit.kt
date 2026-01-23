package com.worldcoin.idkit_kotlin

import com.worldcoin.idkit_kotlin.impl.session.SessionFactory
import kotlinx.coroutines.flow.Flow
import java.net.URL

/**
 * Main entry point for the IDKit SDK.
 * 
 * IDKit provides a simple Kotlin interface for prompting users for World ID proofs.
 * The library handles the entire verification flow, including session creation
 * and result polling.
 */
object IDKit {
    /**
     * Creates a new World ID verification session.
     * This function performs network I/O and automatically switches to [kotlinx.coroutines.Dispatchers.IO].
     * It is safe to call from any coroutine context (including Main).
     *
     * @param appID Your app's unique identifier
     * @param action The action identifier for this verification
     * @param verificationLevel The required verification level
     * @param bridgeURL The bridge server URL (defaults to production)
     * @param signal An optional signal string
     * @param actionDescription Optional human-readable description of the action
     * @return A new Session instance
     * 
     * @throws AppErrorThrowable if the network request fails or JSON parsing fails
     */
    suspend fun createSession(
        appID: AppID,
        action: String,
        verificationLevel: VerificationLevel = VerificationLevel.ORB,
        bridgeURL: BridgeURL = BridgeURL.default,
        signal: String = "",
        actionDescription: String? = null
    ): Session = SessionFactory.create(
        appID = appID,
        action = action,
        verificationLevel = verificationLevel,
        bridgeURL = bridgeURL,
        signal = signal,
        actionDescription = actionDescription
    )
    
    /**
     * Creates a new credential category verification session.
     * This function performs network I/O and automatically switches to [kotlinx.coroutines.Dispatchers.IO].
     * It is safe to call from any coroutine context (including Main).
     *
     * @param appID Your app's unique identifier
     * @param action The action identifier for this verification
     * @param credentialCategory The set of required credential categories (must not be empty)
     * @param bridgeURL The bridge server URL (defaults to production)
     * @param signal An optional signal string
     * @param actionDescription Optional human-readable description of the action
     * @return A new Session instance
     *
     * @throws AppErrorThrowable if credentialCategory is empty, the network request fails, or JSON parsing fails
     */
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
        
        return SessionFactory.createCredentialCategorySession(
            appID = appID,
            action = action,
            credentialCategory = credentialCategory,
            bridgeURL = bridgeURL,
            signal = signal,
            actionDescription = actionDescription
        )
    }
}

/**
 * Represents a World ID verification session.
 * 
 * A session encapsulates the state and behavior needed to perform a World ID verification.
 * It manages the encrypted communication with the Bridge server and provides [Status] updates.
 */
interface Session {
    /**
     * The [URL] that the user should be directed to in order to connect their World App to the client.
     * For example, this URL can be displayed as a QR code or used as a deep link.
     */
    val connectUrl: URL
    
    /**
     * Returns a [Flow] that emits [Status] updates for this verification session.
     * The [Flow] performs network I/O operations and automatically executes on [kotlinx.coroutines.Dispatchers.IO].
     * Collection of the Flow can happen on any dispatcher (e.g., Main for UI updates).
     * 
     * The [Flow] will continue emitting status updates until the verification is complete
     * (either successfully confirmed or failed). Status updates are emitted whenever
     * the status changes.
     *
     * @return A [Flow] of [Status] updates
     */
    fun status(): Flow<Status>
}

/**
 * Represents the current status of a World ID verification session.
 * 
 * The verification process goes through several states:
 * 1. [WaitingForConnection] - Waiting for the user to connect (e.g., scan a QR code or use a deep link)
 * 2. [AwaitingConfirmation] - User connected, waiting for confirmation
 * 3. [Confirmed] - Verification completed successfully with proof
 * 4. [Failed] - Verification failed with an error
 */
sealed class Status {
    /**
     * The session is waiting for the user to connect with their World App
     * (e.g., by scanning a QR code or using a deep link).
     */
    object WaitingForConnection : Status()
    
    /**
     * The user has connected to the session and it is awaiting their confirmation.
     */
    object AwaitingConfirmation : Status()
    
    /**
     * The verification has been completed successfully.
     * 
     * @param proof The cryptographic proof of the verification
     */
    data class Confirmed(val proof: Proof) : Status()
    
    /**
     * The verification has failed.
     * 
     * @param error Details about what went wrong
     */
    data class Failed(val error: AppError) : Status()
}
