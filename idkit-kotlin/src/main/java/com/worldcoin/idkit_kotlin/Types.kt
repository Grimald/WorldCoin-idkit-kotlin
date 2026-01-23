package com.worldcoin.idkit_kotlin

import com.worldcoin.idkit_kotlin.impl.AppErrorSerializer
import com.worldcoin.idkit_kotlin.impl.AppIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.MalformedURLException
import java.net.URL

@Serializable
sealed interface Proof {

    @Serializable
    enum class CredentialType {
        @SerialName("orb")
        ORB,

        @SerialName("secure_document")
        SECURE_DOCUMENT,

        @SerialName("document")
        DOCUMENT,

        @SerialName("device")
        DEVICE,
    }

    @Serializable
    data class Default(
        @SerialName("proof")
        val proof: String,
        @SerialName("merkle_root")
        val merkleRoot: String,
        @SerialName("nullifier_hash")
        val nullifierHash: String,
        @SerialName("credential_type")
        val credentialType: CredentialType,
        @SerialName("verification_level")
        val verificationLevel: CredentialType,
    ) : Proof

    @Serializable
    data class CredentialCategory(
        @SerialName("response")
        val response: List<Response>,
        @SerialName("query")
        val credentialCategory: List<com.worldcoin.idkit_kotlin.CredentialCategory>,
    ) : Proof {
        @Serializable
        data class Response(
            @SerialName("action")
            val action: String,
            @SerialName("proof")
            val proof: String,
            @SerialName("merkle_root")
            val merkleRoot: String,
            @SerialName("nullifier_hash")
            val nullifierHash: String,
            @SerialName("verification_level")
            val verificationLevel: CredentialType,
        )
    }
}

enum class VerificationLevel {
    @SerialName("orb")
    ORB,

    @SerialName("secure_document")
    SECURE_DOCUMENT,

    @SerialName("document")
    DOCUMENT,

    @SerialName("device")
    DEVICE,
}

@Serializable(with = AppErrorSerializer::class)
sealed interface AppError {
    val message: String

    @Serializable
    @SerialName("connection_failed")
    object ConnectionFailed : AppError {
        override val message =
            "Failed to connect to the World App. Please create a new session and try again."
    }

    @Serializable
    @SerialName("verification_rejected")
    object VerificationRejected : AppError {
        override val message =
            "The user rejected the verification request in the World App."
    }

    @Serializable
    @SerialName("max_verifications_reached")
    object MaxVerificationsReached : AppError {
        override val message =
            "The user already verified the maximum number of times for this action."
    }

    @Serializable
    @SerialName("credential_unavailable")
    object CredentialUnavailable : AppError {
        override val message =
            "The user does not have the verification level required by this app."
    }

    @Serializable
    @SerialName("malformed_request")
    object MalformedRequest : AppError {
        override val message =
            "There was a problem with this request. Please try again or contact the app owner."
    }

    @Serializable
    @SerialName("invalid_network")
    object InvalidNetwork : AppError {
        override val message =
            "Invalid network. If you are the app owner, visit docs.worldcoin.org/test for details."
    }

    @Serializable
    @SerialName("inclusion_proof_failed")
    object InclusionProofFailed : AppError {
        override val message =
            "There was an issue fetching the user's credential. Please try again."
    }

    @Serializable
    @SerialName("inclusion_proof_pending")
    object InclusionProofPending : AppError {
        override val message =
            "The user's identity is still being registered. Please wait a few minutes and try again."
    }

    @Serializable
    @SerialName("unexpected_response")
    object UnexpectedResponse : AppError {
        override val message =
            "Unexpected response from the user's World App. Please try again."
    }

    @Serializable
    @SerialName("failed_by_host_app")
    object FailedByHostApp : AppError {
        override val message =
            "Verification failed by the app. Please contact the app owner for details."
    }

    @Serializable
    @SerialName("generic_error")
    data class GenericError(val reason: String? = null) : AppError {
        override val message =
            "Something unexpected went wrong. Please try again." + reason?.let { "Reason: $it" }
                .orEmpty()
    }

    /**
     * SDK-internal error for invalid input parameters.
     * This error is never received from the Bridge, only created by the SDK.
     */
    data class InvalidInput(override val message: String) : AppError
}

/**
 * Exception thrown when an [AppError] occurs during SDK operations.
 * This exception wraps SDK-specific errors and provides type-safe error handling.
 *
 * @param appError The specific [AppError] that occurred
 */
class AppErrorThrowable(val appError: AppError) : Exception(appError.message)

@Serializable
enum class CredentialCategory {
    /**
     * The set of NFC credentials with no authentication.
     */
    @SerialName("document")
    DOCUMENT,

    /**
     * The set of NFC credentials with active or passive authentication.
     */
    @SerialName("secure_document")
    SECURE_DOCUMENT,

    /**
     * The set of credentials that proof personhood (I.E Iris Code)
     */
    @SerialName("personhood")
    PERSONHOOD,
}

/**
 * Represents a World ID application identifier.
 * App IDs must start with "app_" prefix. Staging app IDs use "app_staging_" prefix.
 *
 * @param rawId The raw application ID string
 */
@Serializable(with = AppIDSerializer::class)
@JvmInline
value class AppID private constructor(val rawId: String) {

    val isStaging: Boolean
        get() = rawId.startsWith("app_staging_")
    
    companion object {
        /**
         * Creates a new AppID, validating that it starts with "app_".
         *
         * @throws IllegalArgumentException if the ID doesn't start with "app_"
         */
        operator fun invoke(rawId: String): AppID {
            require(rawId.startsWith("app_")) { 
                "Invalid App ID: must start with 'app_', got: '$rawId'" 
            }
            return AppID(rawId)
        }
    }
}

@Serializable
data class BridgeURL(val rawURL: String) {

    companion object {
        val default = BridgeURL("https://bridge.worldcoin.org")
    }

    init {
        validate()
    }

    private fun validate() {
        val url = try {
            URL(rawURL)
        } catch (e: MalformedURLException) {
            throw IllegalArgumentException("Invalid URL format: $rawURL", e)
        }

        // Allow localhost for development
        if (isLocalhost(url)) return

        require(url.protocol == "https") {
            "Bridge URL must use HTTPS (got: ${url.protocol})."
        }
        require(url.port == -1) {
            "Bridge URL must not specify a port (got: ${url.port})."
        }
        require(url.path.isEmpty() || url.path == "/") {
            "Bridge URL must not contain a path (got: '${url.path}')."
        }
        require(url.query == null) {
            "Bridge URL must not contain a query string (got: '${url.query}')."
        }
        require(url.ref == null) {
            "Bridge URL must not contain a fragment (got: '#${url.ref}')."
        }
    }

    private fun isLocalhost(url: URL): Boolean =
        url.host == "localhost" || url.host == "127.0.0.1"
}
