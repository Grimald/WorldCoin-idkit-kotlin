package com.worldcoin.idkit_kotlin.impl

import com.worldcoin.idkit_kotlin.CredentialCategory
import com.worldcoin.idkit_kotlin.Proof
import com.worldcoin.idkit_kotlin.VerificationLevel
import com.worldcoin.idkit_kotlin.AppID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Marker interface for payloads that can be encrypted.
 */
@Serializable
internal sealed interface EncryptablePayload

/**
 * Request payload for creating a standard verification session.
 */
@Serializable
internal data class CreateRequestPayload(
    @SerialName("app_id")
    val appId: String,
    @SerialName("action")
    val action: String,
    @SerialName("signal")
    val signal: String,
    @SerialName("action_description")
    val actionDescription: String?,
    @SerialName("verification_level")
    val verificationLevel: VerificationLevel,
    @SerialName("credential_types")
    val credentialTypes: List<Proof.CredentialType>
) : EncryptablePayload

/**
 * Creates a CreateRequestPayload from an AppID value class.
 */
internal fun CreateRequestPayload(
    appID: AppID,
    action: String,
    signal: String,
    actionDescription: String?,
    verificationLevel: VerificationLevel
): CreateRequestPayload = CreateRequestPayload(
    appId = appID.rawId,
    action = action,
    signal = signal,
    actionDescription = actionDescription,
    verificationLevel = verificationLevel,
    credentialTypes = when (verificationLevel) {
        VerificationLevel.ORB -> listOf(Proof.CredentialType.ORB)
        VerificationLevel.SECURE_DOCUMENT -> listOf(
            Proof.CredentialType.ORB,
            Proof.CredentialType.SECURE_DOCUMENT
        )
        VerificationLevel.DOCUMENT -> listOf(
            Proof.CredentialType.ORB,
            Proof.CredentialType.SECURE_DOCUMENT,
            Proof.CredentialType.DOCUMENT
        )
        else -> listOf(Proof.CredentialType.ORB, Proof.CredentialType.DEVICE)
    }
)

/**
 * Request payload for creating a credential category verification session.
 */
@Serializable
internal data class CreateCredentialCategoryRequestPayload(
    @SerialName("app_id")
    val appId: String,
    @SerialName("action")
    val action: String,
    @SerialName("signal")
    val signal: String,
    @SerialName("action_description")
    val actionDescription: String?,
    @SerialName("credential_category")
    val credentialCategory: Set<CredentialCategory>,
) : EncryptablePayload

/**
 * Creates a CreateCredentialCategoryRequestPayload from an AppID value class.
 */
internal fun CreateCredentialCategoryRequestPayload(
    appID: AppID,
    action: String,
    signal: String,
    actionDescription: String?,
    credentialCategory: Set<CredentialCategory>,
): CreateCredentialCategoryRequestPayload = CreateCredentialCategoryRequestPayload(
    appId = appID.rawId,
    action = action,
    signal = signal,
    actionDescription = actionDescription,
    credentialCategory = credentialCategory,
)
