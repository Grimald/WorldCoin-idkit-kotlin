package com.worldcoin.idkit_kotlin.impl

import com.worldcoin.idkit_kotlin.AppError
import com.worldcoin.idkit_kotlin.AppID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive

/**
 * Custom serializers for public API types.
 */

internal object AppIDSerializer : KSerializer<AppID> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("AppID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: AppID) {
        encoder.encodeString(value.rawId)
    }

    override fun deserialize(decoder: Decoder): AppID {
        val rawId = decoder.decodeString()
        return AppID(rawId) 
    }
}

internal object AppErrorSerializer : KSerializer<AppError> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("AppError", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: AppError) {
        encoder.encodeString(value.message)
    }

    override fun deserialize(decoder: Decoder): AppError {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("This class can be loaded only by JSON")
        val jsonPrimitive = jsonDecoder.decodeJsonElement().jsonPrimitive

        val errorString = jsonPrimitive.content

        return when (errorString) {
            "connection_failed" -> AppError.ConnectionFailed
            "verification_rejected" -> AppError.VerificationRejected
            "max_verifications_reached" -> AppError.MaxVerificationsReached
            "credential_unavailable" -> AppError.CredentialUnavailable
            "malformed_request" -> AppError.MalformedRequest
            "invalid_network" -> AppError.InvalidNetwork
            "inclusion_proof_failed" -> AppError.InclusionProofFailed
            "inclusion_proof_pending" -> AppError.InclusionProofPending
            "unexpected_response" -> AppError.UnexpectedResponse
            "failed_by_host_app" -> AppError.FailedByHostApp
            "generic_error" -> AppError.GenericError()
            else -> AppError.GenericError("Unknown error: $errorString")
        }
    }
}
